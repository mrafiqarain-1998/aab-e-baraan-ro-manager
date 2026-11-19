const { test } = require("node:test");
const assert = require("node:assert/strict");
const {
  PROJECT_ID,
  DATABASE_ID,
  DEFAULT_PLANT_ID,
  SUPER_ADMIN_EMAILS,
  hashPasswordScrypt,
  verifyPasswordScrypt,
  createAuthCore,
} = require("../src/authCore");

function createInMemoryFirestore() {
  const store = new Map();

  function getColMap(name) {
    if (!store.has(name)) store.set(name, new Map());
    return store.get(name);
  }

  function clone(val) {
    return val === undefined ? undefined : JSON.parse(JSON.stringify(val));
  }

  return {
    _rawStore: store,
    collection(colName) {
      const colMap = getColMap(colName);
      return {
        doc(docId) {
          return {
            async get() {
              const data = colMap.get(docId);
              return {
                id: docId,
                exists: data !== undefined,
                data: () => clone(data),
              };
            },
            async set(data, options = {}) {
              const prev = options.merge ? colMap.get(docId) || {} : {};
              colMap.set(docId, { ...prev, ...clone(data) });
            },
            async delete() {
              colMap.delete(docId);
            },
          };
        },
        where(field, op, value) {
          let limitCount = Infinity;
          const queryObj = {
            limit(n) {
              limitCount = n;
              return queryObj;
            },
            async get() {
              const docs = [];
              for (const [id, data] of colMap.entries()) {
                if (op === "==" && data[field] === value) {
                  docs.push({ id, exists: true, data: () => clone(data) });
                }
              }
              const sliced = docs.slice(0, limitCount);
              return { empty: sliced.length === 0, docs: sliced };
            },
          };
          return queryObj;
        },
      };
    },
  };
}

function createMockAuth(tokensByBearer = {}) {
  const customClaimsByUid = new Map();
  const revokedUids = new Set();

  return {
    customClaimsByUid,
    revokedUids,
    async verifyIdToken(token) {
      if (!tokensByBearer[token]) {
        throw new Error("Invalid ID token");
      }
      return tokensByBearer[token];
    },
    async createCustomToken(uid, claims) {
      return `mock-custom-token:${uid}:${JSON.stringify(claims)}`;
    },
    async setCustomUserClaims(uid, claims) {
      customClaimsByUid.set(uid, claims);
    },
    async revokeRefreshTokens(uid) {
      revokedUids.add(uid);
    },
  };
}

test("Configuration enforces new project aab-e-baraan-ro-manager, named DB, and single Super Admin", () => {
  assert.equal(PROJECT_ID, "aab-e-baraan-ro-manager");
  assert.equal(DATABASE_ID, "ai-studio-android-e454ad39-56bf-4477-a4f8-70f0a5b3b75f");
  assert.equal(SUPER_ADMIN_EMAILS.has("mrafiqarain1998@gmail.com"), true);
  assert.equal(SUPER_ADMIN_EMAILS.has("muhammadrafiq.utopia@gmail.com"), false);
  assert.equal(SUPER_ADMIN_EMAILS.size, 1);
});

test("scrypt salted hashing verifies valid passwords and rejects wrong passwords", async () => {
  const hash = await hashPasswordScrypt("AabPlant#2026", "0123456789abcdef0123456789abcdef");
  assert.equal(await verifyPasswordScrypt("AabPlant#2026", "0123456789abcdef0123456789abcdef", hash), true);
  assert.equal(await verifyPasswordScrypt("WrongPass", "0123456789abcdef0123456789abcdef", hash), false);
});

test("Health check reports new project ID and named Firestore database ID", async () => {
  const db = createInMemoryFirestore();
  const auth = createMockAuth();
  const core = createAuthCore({ db, auth });

  const resp = await core.dispatch("GET", "/health");
  assert.equal(resp.status, 200);
  assert.equal(resp.body.ok, true);
  assert.equal(resp.body.projectId, "aab-e-baraan-ro-manager");
  assert.equal(resp.body.databaseId, "ai-studio-android-e454ad39-56bf-4477-a4f8-70f0a5b3b75f");
});

test("Bootstrap Super Admin allows only mrafiqarain1998@gmail.com and rejects removed admin email", async () => {
  const db = createInMemoryFirestore();
  const auth = createMockAuth({
    "new-admin-token": {
      uid: "admin_uid_01",
      email: "mrafiqarain1998@gmail.com",
      email_verified: true,
      name: "Muhammad Rafiq",
    },
    "removed-admin-token": {
      uid: "old_admin_uid",
      email: "muhammadrafiq.utopia@gmail.com",
      email_verified: true,
      name: "Old Account",
    },
  });
  const core = createAuthCore({ db, auth });

  const rejected = await core.dispatch(
    "POST",
    "/admin/bootstrap",
    { authorization: "Bearer removed-admin-token" },
    { plantId: DEFAULT_PLANT_ID, plantName: "Main Plant" }
  );
  assert.equal(rejected.status, 403);
  assert.equal(rejected.body.errorCode, "SUPER_ADMIN_ONLY");

  const allowed = await core.dispatch(
    "POST",
    "/admin/bootstrap",
    { authorization: "Bearer new-admin-token" },
    { plantId: DEFAULT_PLANT_ID, plantName: "Aab-e-Baraan Main Plant" }
  );
  assert.equal(allowed.status, 200);
  assert.equal(allowed.body.ok, true);
  assert.equal(allowed.body.account.isSuperAdmin, true);
  assert.equal(allowed.body.account.googleEmail, "mrafiqarain1998@gmail.com");
  assert.equal(auth.customClaimsByUid.get("admin_uid_01").isSuperAdmin, true);
});

test("Staff create, secret isolation, custom-token login, password reset, toggle-active, and audit logs", async () => {
  const db = createInMemoryFirestore();
  const auth = createMockAuth({
    "admin-token": {
      uid: "admin_uid_01",
      email: "mrafiqarain1998@gmail.com",
      email_verified: true,
      name: "Muhammad Rafiq",
    },
    "staff-token": {
      uid: "staff_rashid_01",
    },
  });
  const core = createAuthCore({ db, auth });

  await core.dispatch(
    "POST",
    "/admin/bootstrap",
    { authorization: "Bearer admin-token" },
    { plantId: DEFAULT_PLANT_ID }
  );

  // Create staff member (with Google email so staffByEmail is also indexed)
  const created = await core.dispatch(
    "POST",
    "/staff/create",
    { authorization: "Bearer admin-token" },
    {
      id: "staff_rashid_01",
      uid: "staff_rashid_01",
      username: "rashid1",
      password: "InitialPassword1!",
      name: "Rashid Driver",
      googleEmail: "rashid.plant@gmail.com",
      role: "INCHARGE",
      plantId: DEFAULT_PLANT_ID,
      permissions: {
        DASHBOARD: ["VIEW"],
        DELIVERIES: ["VIEW", "ADD"],
      },
    }
  );
  assert.equal(created.status, 200);
  assert.equal(created.body.ok, true);

  // Verify credentials never leak into public staffUsers / users / staffByEmail docs
  const publicStaff = db._rawStore.get("staffUsers").get("staff_rashid_01");
  assert.equal(publicStaff.password, undefined);
  assert.equal(publicStaff.passwordHash, undefined);
  assert.equal(publicStaff.passwordSalt, undefined);
  assert.ok(db._rawStore.get("staffByEmail").get("rashid.plant@gmail.com"));
  assert.ok(db._rawStore.get("staffCredentials").get("staff_rashid_01").passwordHash);

  // Staff custom token login
  const loginResp = await core.dispatch(
    "POST",
    "/staff/login",
    {},
    { username: "rashid1", password: "InitialPassword1!", deviceInfo: "Pixel 8" },
    "10.0.0.1"
  );
  assert.equal(loginResp.status, 200);
  assert.equal(loginResp.body.ok, true);
  assert.match(loginResp.body.customToken, /mock-custom-token:staff_rashid_01:/);
  assert.match(loginResp.body.customToken, /"appStaff":true/);

  // Self-service change password
  const changed = await core.dispatch(
    "POST",
    "/staff/change-password",
    { authorization: "Bearer staff-token" },
    { currentPassword: "InitialPassword1!", newPassword: "UpdatedPassword2@" }
  );
  assert.equal(changed.status, 200);

  // Old password rejected, new password succeeds
  const badOldLogin = await core.dispatch(
    "POST",
    "/staff/login",
    {},
    { username: "rashid1", password: "InitialPassword1!" },
    "10.0.0.2"
  );
  assert.equal(badOldLogin.status, 401);

  const newLogin = await core.dispatch(
    "POST",
    "/staff/login",
    {},
    { username: "rashid1", password: "UpdatedPassword2@" },
    "10.0.0.2"
  );
  assert.equal(newLogin.status, 200);

  // Admin resets password
  const resetResp = await core.dispatch(
    "POST",
    "/staff/reset-password",
    { authorization: "Bearer admin-token" },
    { uid: "staff_rashid_01", newPassword: "ResetPassword3#" }
  );
  assert.equal(resetResp.status, 200);

  // Toggle inactive immediately blocks login and revokes tokens
  const toggleOff = await core.dispatch(
    "POST",
    "/staff/toggle-active",
    { authorization: "Bearer admin-token" },
    { uid: "staff_rashid_01", active: false }
  );
  assert.equal(toggleOff.status, 200);
  assert.equal(auth.revokedUids.has("staff_rashid_01"), true);

  const blockedLogin = await core.dispatch(
    "POST",
    "/staff/login",
    {},
    { username: "rashid1", password: "ResetPassword3#" },
    "10.0.0.3"
  );
  assert.equal(blockedLogin.status, 403);
  assert.equal(blockedLogin.body.errorCode, "ACCOUNT_INACTIVE");

  // Super Admin protection: cannot deactivate or delete Super Admin
  const adminDeactivate = await core.dispatch(
    "POST",
    "/staff/toggle-active",
    { authorization: "Bearer admin-token" },
    { uid: "admin_uid_01", active: false }
  );
  assert.equal(adminDeactivate.status, 403);
  assert.equal(adminDeactivate.body.errorCode, "SUPER_ADMIN_PROTECTED");

  // Audit logs recorded cleanly
  const logs = Array.from(db._rawStore.get("auditLogs").values()).map((l) => l.action);
  assert.ok(logs.includes("BOOTSTRAP_ADMIN"));
  assert.ok(logs.includes("CREATE_STAFF"));
  assert.ok(logs.includes("LOGIN"));
  assert.ok(logs.includes("CHANGE_PASSWORD"));
  assert.ok(logs.includes("RESET_PASSWORD"));
  assert.ok(logs.includes("TOGGLE_ACTIVE"));
});

test("Login endpoint enforces rate limiting after 5 failed attempts", async () => {
  let fakeNow = 1_700_000_000_000;
  const db = createInMemoryFirestore();
  const auth = createMockAuth({
    "admin-token": { uid: "admin_uid_01", email: "mrafiqarain1998@gmail.com", email_verified: true },
  });
  const core = createAuthCore({ db, auth, nowFn: () => fakeNow });

  await core.dispatch("POST", "/admin/bootstrap", { authorization: "Bearer admin-token" }, {});
  await core.dispatch(
    "POST",
    "/staff/create",
    { authorization: "Bearer admin-token" },
    { uid: "staff_rate", id: "staff_rate", username: "ratetest", password: "CorrectPassword", name: "Rate User" }
  );

  for (let i = 0; i < 5; i++) {
    const r = await core.dispatch("POST", "/staff/login", {}, { username: "ratetest", password: "Wrong" }, "192.168.1.50");
    assert.equal(r.status, 401);
  }

  const sixth = await core.dispatch(
    "POST",
    "/staff/login",
    {},
    { username: "ratetest", password: "CorrectPassword" },
    "192.168.1.50"
  );
  assert.equal(sixth.status, 429);
  assert.equal(sixth.body.errorCode, "RATE_LIMITED");
});
