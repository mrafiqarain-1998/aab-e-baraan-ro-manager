const crypto = require("node:crypto");

const PROJECT_ID = "aab-e-baraan-ro-manager";
const PROJECT_NUMBER = "886730398310";
const DATABASE_ID = "ai-studio-android-e454ad39-56bf-4477-a4f8-70f0a5b3b75f";
const DEFAULT_PLANT_ID = "aab_e_baraan_plant_01";

// Strictly only mrafiqarain1998@gmail.com is Super Admin.
const SUPER_ADMIN_EMAILS = new Set(["mrafiqarain1998@gmail.com"]);

const VALID_ROLES = new Set(["ADMIN", "INCHARGE", "VIEW_ONLY"]);
const RATE_LIMIT_WINDOW_MS = 15 * 60 * 1000;
const RATE_LIMIT_MAX_ATTEMPTS = 5;

function normalizeUsername(raw) {
  return String(raw || "")
    .trim()
    .replace(/^@+/, "")
    .trim()
    .toLowerCase();
}

function normalizeEmail(raw) {
  return String(raw || "").trim().toLowerCase();
}

function isValidId(id) {
  return typeof id === "string" && id.length >= 1 && id.length <= 128 && /^[a-zA-Z0-9_-]+$/.test(id);
}

function generateSalt() {
  return crypto.randomBytes(16).toString("hex");
}

function hashPasswordScrypt(rawPassword, saltHex) {
  return new Promise((resolve, reject) => {
    const clean = String(rawPassword || "");
    const salt = String(saltHex || "");
    if (!clean || !salt) {
      reject(new Error("Password and salt must not be empty"));
      return;
    }
    crypto.scrypt(clean, salt, 32, { N: 16384, r: 8, p: 1 }, (err, derivedKey) => {
      if (err) return reject(err);
      resolve(derivedKey.toString("hex"));
    });
  });
}

async function verifyPasswordScrypt(rawPassword, saltHex, expectedHashHex) {
  if (!rawPassword || !saltHex || !expectedHashHex) return false;
  try {
    const derived = await hashPasswordScrypt(rawPassword, saltHex);
    const a = Buffer.from(derived, "hex");
    const b = Buffer.from(String(expectedHashHex), "hex");
    if (a.length !== b.length || a.length === 0) return false;
    return crypto.timingSafeEqual(a, b);
  } catch {
    return false;
  }
}

function sanitizePermissions(permissions, role, isSuperAdmin) {
  if (isSuperAdmin || role === "ADMIN") {
    const allModules = [
      "DASHBOARD",
      "CUSTOMERS",
      "DELIVERIES",
      "PAYMENTS_BALANCES",
      "EXPENSES",
      "BOTTLE_STOCK",
      "REFILL_BATCHES",
      "BOTTLE_ISSUE",
      "FACTORY_MATERIALS",
      "REPORTS",
      "AUDIT_HISTORY",
      "STAFF_MANAGEMENT",
      "SETTINGS",
    ];
    const full = {};
    for (const mod of allModules) {
      full[mod] = ["VIEW", "ADD", "EDIT", "DELETE", "APPROVE", "EXPORT", "MANAGE"];
    }
    return full;
  }
  const clean = {};
  if (permissions && typeof permissions === "object" && !Array.isArray(permissions)) {
    for (const [k, v] of Object.entries(permissions)) {
      if (Array.isArray(v)) {
        clean[String(k)] = v.map((item) => String(item).trim().toUpperCase()).filter(Boolean);
      }
    }
  }
  if (!clean.DASHBOARD || !clean.DASHBOARD.includes("VIEW")) {
    clean.DASHBOARD = Array.from(new Set([...(clean.DASHBOARD || []), "VIEW"]));
  }
  return clean;
}

function buildSanitizedStaffProfile(input, nowMs) {
  const uid = String(input.uid || input.id || "").trim();
  const id = String(input.id || uid).trim();
  const username = normalizeUsername(input.username);
  const googleEmail = normalizeEmail(input.googleEmail);
  const isSuper = Boolean(input.isSuperAdmin) || SUPER_ADMIN_EMAILS.has(googleEmail);
  const rawRole = String(input.role || "INCHARGE").trim().toUpperCase();
  const role = isSuper ? "ADMIN" : VALID_ROLES.has(rawRole) ? rawRole : "INCHARGE";
  const assignedBranchIds = Array.isArray(input.assignedBranchIds) && input.assignedBranchIds.length > 0
    ? input.assignedBranchIds.map((b) => String(b).trim()).filter(Boolean)
    : [String(input.plantId || input.defaultBranchId || DEFAULT_PLANT_ID).trim() || DEFAULT_PLANT_ID];
  const defaultBranchId = String(input.defaultBranchId || input.plantId || assignedBranchIds[0] || DEFAULT_PLANT_ID).trim();

  return {
    id,
    uid,
    username,
    name: String(input.name || username || "Staff User").trim().slice(0, 120),
    phone: String(input.phone || "").trim().slice(0, 40),
    googleEmail,
    role,
    isSuperAdmin: isSuper,
    permissions: sanitizePermissions(input.permissions, role, isSuper),
    assignedBranchIds,
    defaultBranchId,
    plantId: defaultBranchId,
    active: isSuper ? true : input.active !== false,
    updatedAtMs: nowMs,
  };
}

function isSuperAdminToken(decoded) {
  if (!decoded) return false;
  if (decoded.isSuperAdmin === true) return true;
  const email = normalizeEmail(decoded.email);
  return decoded.email_verified === true && SUPER_ADMIN_EMAILS.has(email);
}

function staffHasAccessToPlant(profile, plantId) {
  if (!profile) return false;
  if (profile.isSuperAdmin) return true;
  const target = String(plantId || DEFAULT_PLANT_ID).trim();
  const branches = Array.isArray(profile.assignedBranchIds) ? profile.assignedBranchIds : [];
  if (branches.length === 0) return true;
  return (
    branches.includes(target) ||
    profile.defaultBranchId === target ||
    profile.plantId === target
  );
}

function staffHasManagePermission(profile) {
  if (!profile || profile.active !== true) return false;
  if (profile.isSuperAdmin === true || profile.role === "ADMIN") return true;
  const perms = profile.permissions && profile.permissions.STAFF_MANAGEMENT;
  return Array.isArray(perms) && perms.includes("MANAGE");
}

function createAuthCore({
  db,
  auth,
  serverTimestampFn = () => new Date(),
  nowFn = () => Date.now(),
}) {
  if (!db) throw new Error("Firestore instance bound to named database is required");
  if (!auth) throw new Error("Firebase Auth instance is required");

  // In-memory rate limiter for /staff/login (keyed by username + remote ip)
  const failedAttempts = new Map();

  function isRateLimited(key) {
    const now = nowFn();
    const record = failedAttempts.get(key);
    if (!record) return false;
    const recent = record.filter((ts) => now - ts < RATE_LIMIT_WINDOW_MS);
    if (recent.length === 0) {
      failedAttempts.delete(key);
      return false;
    }
    failedAttempts.set(key, recent);
    return recent.length >= RATE_LIMIT_MAX_ATTEMPTS;
  }

  function recordFailedAttempt(key) {
    const now = nowFn();
    const existing = failedAttempts.get(key) || [];
    const recent = existing.filter((ts) => now - ts < RATE_LIMIT_WINDOW_MS);
    recent.push(now);
    failedAttempts.set(key, recent);
  }

  function clearFailedAttempts(key) {
    failedAttempts.delete(key);
  }

  async function writeAuditLog({
    plantId = DEFAULT_PLANT_ID,
    actorUid,
    actorUsername = "",
    actorName = "",
    actorRole = "SYSTEM",
    action,
    module = "STAFF_MANAGEMENT",
    recordId = "",
    recordLabel = "",
    previousValue = "",
    updatedValue = "",
    deviceInfo = "CloudFunctions",
  }) {
    const nowMs = nowFn();
    const logId = `log_${nowMs}_${crypto.randomBytes(4).toString("hex")}`;
    await db.collection("auditLogs").doc(logId).set({
      id: logId,
      plantId: String(plantId || DEFAULT_PLANT_ID),
      actorUid: String(actorUid || "system"),
      actorUsername: String(actorUsername || "").slice(0, 64),
      actorName: String(actorName || "").slice(0, 120),
      actorRole: String(actorRole || "SYSTEM").slice(0, 32),
      action: String(action || "ACTION").slice(0, 40),
      module: String(module || "STAFF_MANAGEMENT").slice(0, 64),
      recordId: String(recordId || "").slice(0, 128),
      recordLabel: String(recordLabel || "").slice(0, 240),
      previousValue: String(previousValue || "").slice(0, 500),
      updatedValue: String(updatedValue || "").slice(0, 500),
      deviceInfo: String(deviceInfo || "CloudFunctions").slice(0, 160),
      timestampMillis: nowMs,
      createdAt: serverTimestampFn(),
    });
  }

  async function resolveCallerProfile(decoded) {
    const uid = String(decoded.uid || "").trim();
    const email = normalizeEmail(decoded.email);
    if (uid) {
      const uidSnap = await db.collection("staffUsers").doc(uid).get();
      if (uidSnap.exists) return uidSnap.data();
    }
    if (decoded.email_verified === true && email) {
      const emailSnap = await db.collection("staffByEmail").doc(email).get();
      if (emailSnap.exists) return emailSnap.data();
    }
    return null;
  }

  async function verifyAuthorizedCaller(authHeader, requireManageStaff = false) {
    const header = String(authHeader || "");
    if (!header.startsWith("Bearer ")) {
      return { errorStatus: 401, errorCode: "UNAUTHENTICATED", message: "Missing Bearer ID token" };
    }
    const token = header.slice("Bearer ".length).trim();
    if (!token) {
      return { errorStatus: 401, errorCode: "UNAUTHENTICATED", message: "Empty Bearer ID token" };
    }
    let decoded;
    try {
      decoded = await auth.verifyIdToken(token);
    } catch {
      return { errorStatus: 401, errorCode: "INVALID_TOKEN", message: "Invalid or expired Firebase ID token" };
    }

    const superAdmin = isSuperAdminToken(decoded);
    const profile = await resolveCallerProfile(decoded);

    if (requireManageStaff) {
      const allowed = superAdmin || staffHasManagePermission(profile);
      if (!allowed) {
        return { errorStatus: 403, errorCode: "PERMISSION_DENIED", message: "Staff management permission required" };
      }
    }

    return { decoded, superAdmin, profile };
  }

  async function syncStaffProfileDocs(profile, previousEmail = "") {
    const payload = {
      ...profile,
      updatedAt: serverTimestampFn(),
    };
    await db.collection("staffUsers").doc(profile.uid).set(payload, { merge: true });
    await db.collection("users").doc(profile.id).set(payload, { merge: true });

    const prevClean = normalizeEmail(previousEmail);
    const nextClean = normalizeEmail(profile.googleEmail);
    if (prevClean && prevClean !== nextClean) {
      await db.collection("staffByEmail").doc(prevClean).delete();
    }
    if (nextClean) {
      await db.collection("staffByEmail").doc(nextClean).set(payload, { merge: true });
    }
  }

  async function handleHealth() {
    return {
      status: 200,
      body: {
        ok: true,
        projectId: PROJECT_ID,
        projectNumber: PROJECT_NUMBER,
        databaseId: DATABASE_ID,
        runtime: "nodejs20",
      },
    };
  }

  async function handleBootstrapAdmin(authHeader, body = {}) {
    const caller = await verifyAuthorizedCaller(authHeader, false);
    if (caller.errorStatus) {
      return {
        status: caller.errorStatus,
        body: { ok: false, errorCode: caller.errorCode, message: caller.message },
      };
    }

    const email = normalizeEmail(caller.decoded.email);
    const isVerifiedSuperEmail = caller.decoded.email_verified === true && SUPER_ADMIN_EMAILS.has(email);
    if (!isVerifiedSuperEmail && !caller.superAdmin) {
      return {
        status: 403,
        body: {
          ok: false,
          errorCode: "SUPER_ADMIN_ONLY",
          message: "Only mrafiqarain1998@gmail.com may bootstrap Super Admin.",
        },
      };
    }

    const plantId = String(body.plantId || DEFAULT_PLANT_ID).trim() || DEFAULT_PLANT_ID;
    const plantName = String(body.plantName || "Aab-e-Baraan Main Plant").trim().slice(0, 160);
    const displayName = String(body.name || caller.decoded.name || "Muhammad Rafiq (Admin)").trim().slice(0, 120);
    const nowMs = nowFn();

    await db.collection("plants").doc(plantId).set(
      {
        id: plantId,
        plantName,
        name: plantName,
        city: "Main RO Plant",
        active: true,
        isPrimary: plantId === DEFAULT_PLANT_ID,
        updatedAt: serverTimestampFn(),
      },
      { merge: true }
    );

    const profile = buildSanitizedStaffProfile(
      {
        id: caller.decoded.uid,
        uid: caller.decoded.uid,
        username: email.split("@")[0] || "admin",
        name: displayName,
        googleEmail: email,
        role: "ADMIN",
        isSuperAdmin: true,
        assignedBranchIds: [plantId],
        defaultBranchId: plantId,
        plantId,
        active: true,
      },
      nowMs
    );

    await syncStaffProfileDocs(profile);

    if (typeof auth.setCustomUserClaims === "function") {
      await auth.setCustomUserClaims(caller.decoded.uid, {
        appStaff: true,
        isSuperAdmin: true,
        role: "ADMIN",
        plantId,
      });
    }

    await writeAuditLog({
      plantId,
      actorUid: caller.decoded.uid,
      actorUsername: profile.username,
      actorName: profile.name,
      actorRole: "ADMIN",
      action: "BOOTSTRAP_ADMIN",
      module: "SETTINGS",
      recordId: profile.uid,
      recordLabel: email,
      updatedValue: `Bootstrap Super Admin for ${plantId}`,
      deviceInfo: body.deviceInfo || "AndroidApp",
    });

    return {
      status: 200,
      body: {
        ok: true,
        uid: profile.uid,
        plantId,
        account: profile,
      },
    };
  }

  async function handleStaffLogin(body = {}, remoteIp = "unknown") {
    const cleanUser = normalizeUsername(body.username);
    const rawPassword = String(body.password || "");
    const deviceInfo = String(body.deviceInfo || "AndroidDevice").slice(0, 160);

    if (!cleanUser || !rawPassword) {
      return {
        status: 400,
        body: { ok: false, errorCode: "INVALID_CREDENTIALS", message: "Username and password required." },
      };
    }

    const rateKey = `${cleanUser}|${remoteIp}`;
    if (isRateLimited(rateKey)) {
      return {
        status: 429,
        body: {
          ok: false,
          errorCode: "RATE_LIMITED",
          message: "Too many failed login attempts. Try again in 15 minutes.",
        },
      };
    }

    const snap = await db.collection("staffUsers").where("username", "==", cleanUser).limit(1).get();
    const staffDoc = snap.docs[0];
    if (!staffDoc) {
      recordFailedAttempt(rateKey);
      return {
        status: 401,
        body: { ok: false, errorCode: "INVALID_CREDENTIALS", message: "Invalid username or password." },
      };
    }

    const profile = staffDoc.data();
    const uid = String(profile.uid || staffDoc.id);
    const credSnap = await db.collection("staffCredentials").doc(uid).get();
    const cred = credSnap.exists ? credSnap.data() : null;

    if (!cred) {
      recordFailedAttempt(rateKey);
      return {
        status: 401,
        body: { ok: false, errorCode: "INVALID_CREDENTIALS", message: "No staff credentials configured." },
      };
    }

    const valid =
      (await verifyPasswordScrypt(rawPassword, cred.passwordSalt, cred.passwordHash)) ||
      (await verifyPasswordScrypt(rawPassword.trim(), cred.passwordSalt, cred.passwordHash));

    if (!valid) {
      recordFailedAttempt(rateKey);
      await writeAuditLog({
        plantId: profile.plantId || DEFAULT_PLANT_ID,
        actorUid: uid,
        actorUsername: cleanUser,
        actorName: profile.name || cleanUser,
        actorRole: profile.role || "INCHARGE",
        action: "FAILED_LOGIN",
        module: "DASHBOARD",
        recordId: uid,
        recordLabel: cleanUser,
        updatedValue: "Invalid password attempt",
        deviceInfo,
      });
      return {
        status: 401,
        body: { ok: false, errorCode: "INVALID_CREDENTIALS", message: "Invalid username or password." },
      };
    }

    if (profile.active === false || cred.active === false) {
      return {
        status: 403,
        body: { ok: false, errorCode: "ACCOUNT_INACTIVE", message: "This staff account is deactivated." },
      };
    }

    clearFailedAttempts(rateKey);
    const plantId = String(profile.defaultBranchId || profile.plantId || DEFAULT_PLANT_ID);
    const customToken = await auth.createCustomToken(uid, {
      appStaff: true,
      plantId,
      role: String(profile.role || "INCHARGE"),
      isSuperAdmin: Boolean(profile.isSuperAdmin),
    });

    await writeAuditLog({
      plantId,
      actorUid: uid,
      actorUsername: cleanUser,
      actorName: profile.name || cleanUser,
      actorRole: profile.role || "INCHARGE",
      action: "LOGIN",
      module: "DASHBOARD",
      recordId: uid,
      recordLabel: `${profile.name || cleanUser} (@${cleanUser})`,
      updatedValue: "Staff Custom Token Sign-In",
      deviceInfo,
    });

    return {
      status: 200,
      body: {
        ok: true,
        customToken,
        uid,
        plantId,
        account: profile,
      },
    };
  }

  async function handleStaffCreate(authHeader, body = {}) {
    const caller = await verifyAuthorizedCaller(authHeader, true);
    if (caller.errorStatus) {
      return {
        status: caller.errorStatus,
        body: { ok: false, errorCode: caller.errorCode, message: caller.message },
      };
    }

    const cleanUser = normalizeUsername(body.username);
    const rawPassword = String(body.password || "");
    if (!cleanUser || rawPassword.length < 4) {
      return {
        status: 400,
        body: { ok: false, errorCode: "INVALID_INPUT", message: "Username and password (>=4 chars) required." },
      };
    }

    const dupSnap = await db.collection("staffUsers").where("username", "==", cleanUser).limit(1).get();
    if (!dupSnap.empty) {
      return {
        status: 409,
        body: { ok: false, errorCode: "USERNAME_EXISTS", message: "Username already exists." },
      };
    }

    const targetPlantId = String(body.plantId || body.defaultBranchId || DEFAULT_PLANT_ID).trim() || DEFAULT_PLANT_ID;
    if (!caller.superAdmin && caller.profile && !staffHasAccessToPlant(caller.profile, targetPlantId)) {
      return {
        status: 403,
        body: { ok: false, errorCode: "PLANT_ISOLATION_VIOLATION", message: "Cannot create staff outside assigned plant." },
      };
    }

    const requestedUid = isValidId(body.uid) ? body.uid : isValidId(body.id) ? body.id : `staff_${cleanUser}_${crypto.randomBytes(3).toString("hex")}`;
    const nowMs = nowFn();
    const profile = buildSanitizedStaffProfile(
      {
        ...body,
        id: isValidId(body.id) ? body.id : requestedUid,
        uid: requestedUid,
        username: cleanUser,
        isSuperAdmin: false, // Never escalate non-allowlisted user via API
        plantId: targetPlantId,
      },
      nowMs
    );

    const salt = generateSalt();
    const passwordHash = await hashPasswordScrypt(rawPassword, salt);

    await db.collection("staffCredentials").doc(profile.uid).set({
      uid: profile.uid,
      username: cleanUser,
      passwordSalt: salt,
      passwordHash,
      hashAlgo: "scrypt",
      active: profile.active,
      plantId: targetPlantId,
      updatedAt: serverTimestampFn(),
    });

    await syncStaffProfileDocs(profile);

    await writeAuditLog({
      plantId: targetPlantId,
      actorUid: caller.decoded.uid,
      actorUsername: caller.profile?.username || caller.decoded.email || "admin",
      actorName: caller.profile?.name || caller.decoded.name || "Admin",
      actorRole: caller.superAdmin ? "ADMIN" : caller.profile?.role || "INCHARGE",
      action: "CREATE_STAFF",
      module: "STAFF_MANAGEMENT",
      recordId: profile.uid,
      recordLabel: `${profile.name} (@${profile.username})`,
      updatedValue: `Role=${profile.role}, Plant=${targetPlantId}`,
      deviceInfo: body.deviceInfo || "AndroidApp",
    });

    return {
      status: 200,
      body: {
        ok: true,
        uid: profile.uid,
        plantId: targetPlantId,
        account: profile,
      },
    };
  }

  async function handleStaffUpdate(authHeader, body = {}) {
    const caller = await verifyAuthorizedCaller(authHeader, true);
    if (caller.errorStatus) {
      return {
        status: caller.errorStatus,
        body: { ok: false, errorCode: caller.errorCode, message: caller.message },
      };
    }

    const uid = String(body.uid || body.id || "").trim();
    if (!isValidId(uid)) {
      return { status: 400, body: { ok: false, errorCode: "INVALID_INPUT", message: "Valid uid required." } };
    }

    const existingSnap = await db.collection("staffUsers").doc(uid).get();
    const existing = existingSnap.exists ? existingSnap.data() : null;
    if (existing && (existing.isSuperAdmin === true || existing.id === "user_admin_01")) {
      if (body.active === false || String(body.role || "ADMIN").toUpperCase() !== "ADMIN") {
        return {
          status: 403,
          body: { ok: false, errorCode: "SUPER_ADMIN_PROTECTED", message: "Super Admin cannot be demoted or deactivated." },
        };
      }
    }

    const targetPlantId = String(body.plantId || body.defaultBranchId || existing?.plantId || DEFAULT_PLANT_ID);
    if (!caller.superAdmin && caller.profile && !staffHasAccessToPlant(caller.profile, targetPlantId)) {
      return {
        status: 403,
        body: { ok: false, errorCode: "PLANT_ISOLATION_VIOLATION", message: "Cannot modify staff outside assigned plant." },
      };
    }

    const profile = buildSanitizedStaffProfile(
      {
        ...existing,
        ...body,
        uid,
        id: String(body.id || existing?.id || uid),
        isSuperAdmin: Boolean(existing?.isSuperAdmin) || SUPER_ADMIN_EMAILS.has(normalizeEmail(body.googleEmail || existing?.googleEmail)),
      },
      nowFn()
    );

    await syncStaffProfileDocs(profile, existing?.googleEmail || "");
    await db.collection("staffCredentials").doc(uid).set(
      { username: profile.username, active: profile.active, plantId: profile.plantId, updatedAt: serverTimestampFn() },
      { merge: true }
    );

    await writeAuditLog({
      plantId: profile.plantId,
      actorUid: caller.decoded.uid,
      actorUsername: caller.profile?.username || caller.decoded.email || "admin",
      actorName: caller.profile?.name || "Admin",
      actorRole: caller.superAdmin ? "ADMIN" : caller.profile?.role || "INCHARGE",
      action: "UPDATE_STAFF",
      module: "STAFF_MANAGEMENT",
      recordId: uid,
      recordLabel: `${profile.name} (@${profile.username})`,
      updatedValue: `Role=${profile.role}, Active=${profile.active}`,
      deviceInfo: body.deviceInfo || "AndroidApp",
    });

    return {
      status: 200,
      body: { ok: true, uid, plantId: profile.plantId, account: profile },
    };
  }

  async function handleStaffResetPassword(authHeader, body = {}) {
    const caller = await verifyAuthorizedCaller(authHeader, true);
    if (caller.errorStatus) {
      return {
        status: caller.errorStatus,
        body: { ok: false, errorCode: caller.errorCode, message: caller.message },
      };
    }

    const uid = String(body.uid || "").trim();
    const newPassword = String(body.newPassword || "");
    if (!isValidId(uid) || newPassword.length < 4) {
      return {
        status: 400,
        body: { ok: false, errorCode: "INVALID_INPUT", message: "Valid uid and newPassword (>=4 chars) required." },
      };
    }

    const staffSnap = await db.collection("staffUsers").doc(uid).get();
    const staff = staffSnap.exists ? staffSnap.data() : null;
    if (!staff) {
      return { status: 404, body: { ok: false, errorCode: "NOT_FOUND", message: "Staff user not found." } };
    }
    if (!caller.superAdmin && caller.profile && !staffHasAccessToPlant(caller.profile, staff.plantId)) {
      return {
        status: 403,
        body: { ok: false, errorCode: "PLANT_ISOLATION_VIOLATION", message: "Cannot reset password outside assigned plant." },
      };
    }

    const salt = generateSalt();
    const passwordHash = await hashPasswordScrypt(newPassword, salt);
    await db.collection("staffCredentials").doc(uid).set(
      {
        uid,
        username: staff.username,
        passwordSalt: salt,
        passwordHash,
        hashAlgo: "scrypt",
        active: staff.active !== false,
        plantId: staff.plantId || DEFAULT_PLANT_ID,
        updatedAt: serverTimestampFn(),
      },
      { merge: true }
    );

    await writeAuditLog({
      plantId: staff.plantId || DEFAULT_PLANT_ID,
      actorUid: caller.decoded.uid,
      actorUsername: caller.profile?.username || caller.decoded.email || "admin",
      actorName: caller.profile?.name || "Admin",
      actorRole: caller.superAdmin ? "ADMIN" : caller.profile?.role || "INCHARGE",
      action: "RESET_PASSWORD",
      module: "STAFF_MANAGEMENT",
      recordId: uid,
      recordLabel: `@${staff.username}`,
      updatedValue: "Admin Password Reset Completed",
      deviceInfo: body.deviceInfo || "AndroidApp",
    });

    return {
      status: 200,
      body: { ok: true, uid, plantId: staff.plantId || DEFAULT_PLANT_ID, message: "Password updated." },
    };
  }

  async function handleStaffDelete(authHeader, body = {}) {
    const caller = await verifyAuthorizedCaller(authHeader, true);
    if (caller.errorStatus) {
      return {
        status: caller.errorStatus,
        body: { ok: false, errorCode: caller.errorCode, message: caller.message },
      };
    }

    const uid = String(body.uid || "").trim();
    if (!isValidId(uid)) {
      return { status: 400, body: { ok: false, errorCode: "INVALID_INPUT", message: "Valid uid required." } };
    }

    const staffSnap = await db.collection("staffUsers").doc(uid).get();
    const staff = staffSnap.exists ? staffSnap.data() : null;
    if (staff && (staff.isSuperAdmin === true || staff.id === "user_admin_01")) {
      return {
        status: 403,
        body: { ok: false, errorCode: "SUPER_ADMIN_PROTECTED", message: "Super Admin cannot be deleted." },
      };
    }
    if (!caller.superAdmin && caller.profile && staff && !staffHasAccessToPlant(caller.profile, staff.plantId)) {
      return {
        status: 403,
        body: { ok: false, errorCode: "PLANT_ISOLATION_VIOLATION", message: "Cannot delete staff outside assigned plant." },
      };
    }

    await db.collection("staffCredentials").doc(uid).delete();
    await db.collection("staffUsers").doc(uid).delete();
    if (staff?.id) {
      await db.collection("users").doc(staff.id).delete();
    }
    if (staff?.googleEmail) {
      await db.collection("staffByEmail").doc(normalizeEmail(staff.googleEmail)).delete();
    }
    if (typeof auth.revokeRefreshTokens === "function") {
      try {
        await auth.revokeRefreshTokens(uid);
      } catch {
        // Ignore if uid has no Firebase Auth record yet
      }
    }

    await writeAuditLog({
      plantId: staff?.plantId || body.plantId || DEFAULT_PLANT_ID,
      actorUid: caller.decoded.uid,
      actorUsername: caller.profile?.username || caller.decoded.email || "admin",
      actorName: caller.profile?.name || "Admin",
      actorRole: caller.superAdmin ? "ADMIN" : caller.profile?.role || "INCHARGE",
      action: "DELETE_STAFF",
      module: "STAFF_MANAGEMENT",
      recordId: uid,
      recordLabel: staff?.username ? `@${staff.username}` : uid,
      updatedValue: "Deleted",
      deviceInfo: body.deviceInfo || "AndroidApp",
    });

    return { status: 200, body: { ok: true, uid } };
  }

  async function handleStaffToggleActive(authHeader, body = {}) {
    const caller = await verifyAuthorizedCaller(authHeader, true);
    if (caller.errorStatus) {
      return {
        status: caller.errorStatus,
        body: { ok: false, errorCode: caller.errorCode, message: caller.message },
      };
    }

    const uid = String(body.uid || "").trim();
    if (!isValidId(uid)) {
      return { status: 400, body: { ok: false, errorCode: "INVALID_INPUT", message: "Valid uid required." } };
    }

    const staffSnap = await db.collection("staffUsers").doc(uid).get();
    const staff = staffSnap.exists ? staffSnap.data() : null;
    if (!staff) {
      return { status: 404, body: { ok: false, errorCode: "NOT_FOUND", message: "Staff user not found." } };
    }
    if (staff.isSuperAdmin === true || staff.id === "user_admin_01") {
      return {
        status: 403,
        body: { ok: false, errorCode: "SUPER_ADMIN_PROTECTED", message: "Super Admin cannot be deactivated." },
      };
    }

    const nextActive = typeof body.active === "boolean" ? body.active : !staff.active;
    const updated = { ...staff, active: nextActive, updatedAtMs: nowFn() };
    await syncStaffProfileDocs(updated);
    await db.collection("staffCredentials").doc(uid).set(
      { active: nextActive, updatedAt: serverTimestampFn() },
      { merge: true }
    );
    if (!nextActive && typeof auth.revokeRefreshTokens === "function") {
      try {
        await auth.revokeRefreshTokens(uid);
      } catch {
        // Ignore if uid has no Firebase Auth record yet
      }
    }

    await writeAuditLog({
      plantId: staff.plantId || DEFAULT_PLANT_ID,
      actorUid: caller.decoded.uid,
      actorUsername: caller.profile?.username || caller.decoded.email || "admin",
      actorName: caller.profile?.name || "Admin",
      actorRole: caller.superAdmin ? "ADMIN" : caller.profile?.role || "INCHARGE",
      action: "TOGGLE_ACTIVE",
      module: "STAFF_MANAGEMENT",
      recordId: uid,
      recordLabel: `@${staff.username}`,
      previousValue: String(staff.active),
      updatedValue: String(nextActive),
      deviceInfo: body.deviceInfo || "AndroidApp",
    });

    return { status: 200, body: { ok: true, uid, plantId: staff.plantId || DEFAULT_PLANT_ID, account: updated } };
  }

  async function handleChangeOwnPassword(authHeader, body = {}) {
    const caller = await verifyAuthorizedCaller(authHeader, false);
    if (caller.errorStatus) {
      return {
        status: caller.errorStatus,
        body: { ok: false, errorCode: caller.errorCode, message: caller.message },
      };
    }

    const currentPassword = String(body.currentPassword || "");
    const newPassword = String(body.newPassword || "");
    if (!currentPassword || newPassword.length < 4) {
      return {
        status: 400,
        body: { ok: false, errorCode: "INVALID_INPUT", message: "Current and new password (>=4 chars) required." },
      };
    }

    const uid = caller.decoded.uid;
    const credSnap = await db.collection("staffCredentials").doc(uid).get();
    const cred = credSnap.exists ? credSnap.data() : null;
    if (!cred) {
      return { status: 404, body: { ok: false, errorCode: "NOT_FOUND", message: "No credentials found for user." } };
    }

    const valid = await verifyPasswordScrypt(currentPassword, cred.passwordSalt, cred.passwordHash);
    if (!valid) {
      return { status: 401, body: { ok: false, errorCode: "INVALID_CREDENTIALS", message: "Current password is incorrect." } };
    }

    const salt = generateSalt();
    const passwordHash = await hashPasswordScrypt(newPassword, salt);
    await db.collection("staffCredentials").doc(uid).set(
      { passwordSalt: salt, passwordHash, hashAlgo: "scrypt", updatedAt: serverTimestampFn() },
      { merge: true }
    );

    await writeAuditLog({
      plantId: caller.profile?.plantId || DEFAULT_PLANT_ID,
      actorUid: uid,
      actorUsername: caller.profile?.username || "",
      actorName: caller.profile?.name || "",
      actorRole: caller.profile?.role || "INCHARGE",
      action: "CHANGE_PASSWORD",
      module: "STAFF_MANAGEMENT",
      recordId: uid,
      recordLabel: caller.profile?.username ? `@${caller.profile.username}` : uid,
      updatedValue: "Self-service password changed",
      deviceInfo: body.deviceInfo || "AndroidApp",
    });

    return { status: 200, body: { ok: true, uid, message: "Password changed successfully." } };
  }

  async function dispatch(method, rawPath, headers = {}, body = {}, remoteIp = "unknown") {
    const cleanMethod = String(method || "GET").toUpperCase();
    const path = String(rawPath || "/").split("?")[0].replace(/\/+$/, "") || "/";
    const authHeader = headers.authorization || headers.Authorization || "";

    if (cleanMethod === "GET" && path === "/health") {
      return handleHealth();
    }
    if (cleanMethod !== "POST") {
      return { status: 405, body: { ok: false, errorCode: "METHOD_NOT_ALLOWED", message: "Use POST for mutations." } };
    }

    switch (path) {
      case "/admin/bootstrap":
        return handleBootstrapAdmin(authHeader, body);
      case "/staff/login":
        return handleStaffLogin(body, remoteIp);
      case "/staff/create":
        return handleStaffCreate(authHeader, body);
      case "/staff/update":
        return handleStaffUpdate(authHeader, body);
      case "/staff/reset-password":
        return handleStaffResetPassword(authHeader, body);
      case "/staff/delete":
        return handleStaffDelete(authHeader, body);
      case "/staff/toggle-active":
        return handleStaffToggleActive(authHeader, body);
      case "/staff/change-password":
        return handleChangeOwnPassword(authHeader, body);
      default:
        return { status: 404, body: { ok: false, errorCode: "NOT_FOUND", message: `Unknown route ${path}` } };
    }
  }

  return {
    dispatch,
    handleHealth,
    handleBootstrapAdmin,
    handleStaffLogin,
    handleStaffCreate,
    handleStaffUpdate,
    handleStaffResetPassword,
    handleStaffDelete,
    handleStaffToggleActive,
    handleChangeOwnPassword,
  };
}

module.exports = {
  PROJECT_ID,
  PROJECT_NUMBER,
  DATABASE_ID,
  DEFAULT_PLANT_ID,
  SUPER_ADMIN_EMAILS,
  generateSalt,
  hashPasswordScrypt,
  verifyPasswordScrypt,
  createAuthCore,
};
