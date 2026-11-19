const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const PLANT_ID = "aab_e_baraan_plant_01";
const OTHER_PLANT_ID = "lahore_branch_02";
const ADMIN_UID = "admin_uid_01";
const ADMIN_EMAIL = "mrafiqarain1998@gmail.com";
const REMOVED_EMAIL = "muhammadrafiq.utopia@gmail.com";
const REMOVED_UID = "removed_old_admin_uid";
const STAFF_CUSTOM_UID = "staff_rashid_01";
const VIEWER_CUSTOM_UID = "staff_viewer_02";
const INACTIVE_UID = "staff_inactive_03";
const OUTSIDER_UID = "outsider_uid_99";
const OUTSIDER_EMAIL = "outsider@other.com";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("staffUsers").get());
  await assertFails(unauthDb.collection("staffCredentials").doc("staff_rashid_01").get());
  await assertFails(unauthDb.collection("customers").where("plantId", "==", PLANT_ID).get());
  await assertFails(unauthDb.collection("auditLogs").where("plantId", "==", PLANT_ID).get());
  await assertFails(unauthDb.collection("passwordResetRequests").get());
});

test("staffCredentials collection is completely blocked from all client reads and writes", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("staffCredentials").doc(STAFF_CUSTOM_UID).set({
      uid: STAFF_CUSTOM_UID,
      username: "rashid1",
      salt: "abc",
      passwordHash: "def",
      active: true,
      plantId: PLANT_ID,
    });
  });

  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true, isSuperAdmin: true })
    .firestore();
  const staffDb = testEnv
    .authenticatedContext(STAFF_CUSTOM_UID, { appStaff: true, plantId: PLANT_ID, role: "INCHARGE" })
    .firestore();

  await assertFails(adminDb.collection("staffCredentials").doc(STAFF_CUSTOM_UID).get());
  await assertFails(adminDb.collection("staffCredentials").get());
  await assertFails(staffDb.collection("staffCredentials").doc(STAFF_CUSTOM_UID).get());
  await assertFails(
    adminDb.collection("staffCredentials").doc("hack").set({
      uid: "hack",
      username: "hack",
      salt: "x",
      passwordHash: "y",
      active: true,
      plantId: PLANT_ID,
    })
  );
});

test("Only mrafiqarain1998@gmail.com has Super Admin email privilege; removed email is denied", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("customers").doc("cust_1").set({
      id: "cust_1",
      plantId: PLANT_ID,
      name: "Al-Madina Hotel",
      active: true,
      ownerId: ADMIN_UID,
      memberUids: [ADMIN_UID],
      memberEmails: [ADMIN_EMAIL],
    });
  });

  const newAdminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();
  const removedEmailDb = testEnv
    .authenticatedContext(REMOVED_UID, { email: REMOVED_EMAIL, email_verified: true })
    .firestore();

  await assertSucceeds(newAdminDb.collection("customers").doc("cust_1").get());
  await assertFails(removedEmailDb.collection("customers").doc("cust_1").get());
  await assertFails(
    removedEmailDb.collection("customers").doc("cust_hack").set({
      id: "cust_hack",
      plantId: PLANT_ID,
      name: "Unauthorized Write",
      active: true,
    })
  );
});

test("Non-Gmail Custom-Token Staff accesses centralized plant data, enforces plant isolation & module RBAC", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("staffUsers").doc(STAFF_CUSTOM_UID).set({
      uid: STAFF_CUSTOM_UID,
      id: STAFF_CUSTOM_UID,
      username: "rashid1",
      name: "Rashid Driver",
      role: "INCHARGE",
      isSuperAdmin: false,
      active: true,
      plantId: PLANT_ID,
      defaultBranchId: PLANT_ID,
      assignedBranchIds: [PLANT_ID],
      permissions: {
        DASHBOARD: ["VIEW"],
        DELIVERIES: ["VIEW", "ADD"],
        CUSTOMERS: ["VIEW"],
        PAYMENTS_BALANCES: ["VIEW", "ADD"],
        BOTTLE_STOCK: ["VIEW"],
        EXPENSES: [],
      },
    });
    await db.collection("customers").doc("cust_1").set({
      id: "cust_1",
      plantId: PLANT_ID,
      name: "Al-Madina Hotel",
      active: true,
      ownerId: ADMIN_UID,
      memberUids: [ADMIN_UID],
    });
    await db.collection("customers").doc("cust_other_plant").set({
      id: "cust_other_plant",
      plantId: OTHER_PLANT_ID,
      name: "Lahore Branch Customer",
      active: true,
      ownerId: ADMIN_UID,
      memberUids: [ADMIN_UID],
    });
    await db.collection("expenses").doc("exp_1").set({
      id: "exp_1",
      plantId: PLANT_ID,
      date: "2026-11-10",
      amount: 5000,
      ownerId: ADMIN_UID,
      memberUids: [ADMIN_UID],
    });
  });

  // Notice: Custom Token staff has NO email claim!
  const staffDb = testEnv
    .authenticatedContext(STAFF_CUSTOM_UID, { appStaff: true, plantId: PLANT_ID, role: "INCHARGE" })
    .firestore();

  // Allowed: Read shared centralized customers even though created by Admin UID
  await assertSucceeds(
    staffDb.collection("customers").where("plantId", "==", PLANT_ID).get()
  );

  // Denied: Cross-plant read/write on unassigned plant branch
  await assertFails(staffDb.collection("customers").doc("cust_other_plant").get());
  await assertFails(
    staffDb.collection("deliveries").doc("del_cross_plant").set({
      id: "del_cross_plant",
      plantId: OTHER_PLANT_ID,
      customerId: "cust_other_plant",
      date: "2026-11-17",
    })
  );

  // Allowed: Add a delivery because DELIVERIES includes ADD
  await assertSucceeds(
    staffDb.collection("deliveries").doc("del_1").set({
      id: "del_1",
      plantId: PLANT_ID,
      customerId: "cust_1",
      customerName: "Al-Madina Hotel",
      date: "2026-11-17",
      totalAmount: 1000,
      ownerId: ADMIN_UID,
      memberUids: [ADMIN_UID],
    })
  );

  // Allowed cross-collection update: Update customer balance after delivery
  await assertSucceeds(
    staffDb.collection("customers").doc("cust_1").update({ currentBalance: 1000 })
  );

  // Denied: Delete customer (CUSTOMERS lacks DELETE)
  await assertFails(staffDb.collection("customers").doc("cust_1").delete());

  // Denied: Read expenses (EXPENSES permission array is empty)
  await assertFails(staffDb.collection("expenses").where("plantId", "==", PLANT_ID).get());
});

test("View-Only staff can read authorized modules but cannot mutate data", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("staffUsers").doc(VIEWER_CUSTOM_UID).set({
      uid: VIEWER_CUSTOM_UID,
      id: VIEWER_CUSTOM_UID,
      username: "viewer1",
      name: "Auditor",
      role: "VIEW_ONLY",
      isSuperAdmin: false,
      active: true,
      plantId: PLANT_ID,
      assignedBranchIds: [PLANT_ID],
      permissions: {
        CUSTOMERS: ["VIEW"],
        DELIVERIES: ["VIEW"],
      },
    });
    await db.collection("customers").doc("cust_1").set({
      id: "cust_1",
      plantId: PLANT_ID,
      name: "Shared Cafe",
      active: true,
      ownerId: ADMIN_UID,
      memberUids: [ADMIN_UID],
    });
  });

  const viewerDb = testEnv
    .authenticatedContext(VIEWER_CUSTOM_UID, { appStaff: true, plantId: PLANT_ID, role: "VIEW_ONLY" })
    .firestore();

  await assertSucceeds(viewerDb.collection("customers").doc("cust_1").get());
  await assertFails(viewerDb.collection("customers").doc("cust_1").update({ name: "Modified" }));
  await assertFails(
    viewerDb.collection("deliveries").doc("del_hack").set({
      id: "del_hack",
      plantId: PLANT_ID,
      customerId: "cust_1",
      date: "2026-11-17",
      ownerId: VIEWER_CUSTOM_UID,
      memberUids: [VIEWER_CUSTOM_UID],
    })
  );
});

test("Deactivated staff account is immediately blocked in Firestore rules", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("staffUsers").doc(INACTIVE_UID).set({
      uid: INACTIVE_UID,
      id: INACTIVE_UID,
      username: "fired_staff",
      name: "Inactive User",
      role: "ADMIN",
      isSuperAdmin: false,
      active: false,
      plantId: PLANT_ID,
      assignedBranchIds: [PLANT_ID],
    });
    await db.collection("customers").doc("cust_1").set({
      id: "cust_1",
      plantId: PLANT_ID,
      name: "Al-Madina Hotel",
      active: true,
    });
  });

  const inactiveDb = testEnv
    .authenticatedContext(INACTIVE_UID, { appStaff: true, plantId: PLANT_ID })
    .firestore();

  await assertFails(inactiveDb.collection("customers").where("plantId", "==", PLANT_ID).get());
  await assertFails(inactiveDb.collection("customers").doc("cust_1").get());
});

test("Super Admin protection and secret-free enforcement in staffUsers and passwordResetRequests", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("staffUsers").doc(ADMIN_UID).set({
      uid: ADMIN_UID,
      id: "user_admin_01",
      username: "admin",
      name: "Muhammad Rafiq",
      role: "ADMIN",
      isSuperAdmin: true,
      active: true,
      plantId: PLANT_ID,
      assignedBranchIds: [PLANT_ID],
    });
    await db.collection("staffUsers").doc(VIEWER_CUSTOM_UID).set({
      uid: VIEWER_CUSTOM_UID,
      id: VIEWER_CUSTOM_UID,
      username: "viewer1",
      name: "Auditor",
      role: "VIEW_ONLY",
      isSuperAdmin: false,
      active: true,
      plantId: PLANT_ID,
      assignedBranchIds: [PLANT_ID],
      permissions: { CUSTOMERS: ["VIEW"] },
    });
    await db.collection("passwordResetRequests").doc("req_1").set({
      id: "req_1",
      plantId: PLANT_ID,
      username: "rashid1",
      isResolved: false,
      newPasswordSet: "",
    });
  });

  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true, isSuperAdmin: true })
    .firestore();
  const viewerDb = testEnv
    .authenticatedContext(VIEWER_CUSTOM_UID, { appStaff: true, plantId: PLANT_ID, role: "VIEW_ONLY" })
    .firestore();

  // Reject storing plaintext password or passwordSalt in staffUsers
  await assertFails(
    adminDb.collection("staffUsers").doc("staff_bad").set({
      uid: "staff_bad",
      id: "staff_bad",
      username: "baduser",
      password: "plaintext123",
      name: "Bad User",
      role: "INCHARGE",
      active: true,
      plantId: PLANT_ID,
    })
  );

  // Reject non-empty newPasswordSet in passwordResetRequests & block non-admin reads
  await assertFails(viewerDb.collection("passwordResetRequests").doc("req_1").get());
  await assertSucceeds(adminDb.collection("passwordResetRequests").doc("req_1").get());
  await assertFails(
    adminDb.collection("passwordResetRequests").doc("req_secret").set({
      id: "req_secret",
      plantId: PLANT_ID,
      username: "rashid1",
      isResolved: true,
      newPasswordSet: "LeakedPassword123",
    })
  );

  // Super Admin cannot be deactivated or demoted
  await assertFails(adminDb.collection("staffUsers").doc(ADMIN_UID).update({ active: false }));
  await assertFails(adminDb.collection("staffUsers").doc(ADMIN_UID).update({ isSuperAdmin: false }));
  await assertFails(adminDb.collection("staffUsers").doc(ADMIN_UID).delete());
});

test("Audit logs allow append-only create by actor and are strictly immutable", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("staffUsers").doc(STAFF_CUSTOM_UID).set({
      uid: STAFF_CUSTOM_UID,
      id: STAFF_CUSTOM_UID,
      username: "rashid1",
      name: "Rashid",
      role: "INCHARGE",
      active: true,
      plantId: PLANT_ID,
    });
  });

  const staffDb = testEnv
    .authenticatedContext(STAFF_CUSTOM_UID, { appStaff: true, plantId: PLANT_ID })
    .firestore();
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true, isSuperAdmin: true })
    .firestore();

  await assertSucceeds(
    staffDb.collection("auditLogs").doc("log_1").set({
      id: "log_1",
      plantId: PLANT_ID,
      actorUid: STAFF_CUSTOM_UID,
      actorUsername: "rashid1",
      actorName: "Rashid",
      actorRole: "INCHARGE",
      action: "CREATE",
      module: "DELIVERIES",
      recordId: "del_1",
      timestampMillis: Date.now(),
    })
  );

  // Even Super Admin cannot tamper with or delete an audit log
  await assertFails(adminDb.collection("auditLogs").doc("log_1").update({ action: "TAMPERED" }));
  await assertFails(adminDb.collection("auditLogs").doc("log_1").delete());

  // Super Admin can read auditLogs
  await assertSucceeds(adminDb.collection("auditLogs").where("plantId", "==", PLANT_ID).get());
});

test("Outsider authenticated user cannot read centralized plant data", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("customers").doc("cust_admin").set({
      id: "cust_admin",
      plantId: PLANT_ID,
      name: "Private Customer",
      active: true,
      ownerId: ADMIN_UID,
      memberUids: [ADMIN_UID],
      memberEmails: [ADMIN_EMAIL],
    });
  });

  const outsiderDb = testEnv
    .authenticatedContext(OUTSIDER_UID, { email: OUTSIDER_EMAIL, email_verified: true })
    .firestore();

  await assertFails(outsiderDb.collection("customers").where("plantId", "==", PLANT_ID).get());
  await assertFails(outsiderDb.collection("customers").doc("cust_admin").get());
  await assertFails(outsiderDb.collection("customers").doc("cust_admin").update({ name: "Hacked" }));
});

test("Staff with PAYMENTS_BALANCES.ADD (without DELIVERIES.EDIT) can update delivery payment status", async () => {
  const CASHIER_UID = "staff_cashier_04";
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("staffUsers").doc(CASHIER_UID).set({
      uid: CASHIER_UID,
      id: CASHIER_UID,
      username: "cashier1",
      name: "Cashier",
      role: "INCHARGE",
      isSuperAdmin: false,
      active: true,
      plantId: PLANT_ID,
      assignedBranchIds: [PLANT_ID],
      permissions: {
        PAYMENTS_BALANCES: ["VIEW", "ADD"],
        DELIVERIES: ["VIEW"],
        CUSTOMERS: ["VIEW"],
      },
    });
    await db.collection("deliveries").doc("del_pay_1").set({
      id: "del_pay_1",
      plantId: PLANT_ID,
      customerId: "cust_1",
      customerName: "Al-Madina Hotel",
      date: "2026-11-17",
      totalAmount: 1000,
      paymentCollected: 0,
      status: "Delivered (Unpaid)",
      ownerId: ADMIN_UID,
      memberUids: [ADMIN_UID],
    });
  });

  const cashierDb = testEnv
    .authenticatedContext(CASHIER_UID, { appStaff: true, plantId: PLANT_ID, role: "INCHARGE" })
    .firestore();

  await assertSucceeds(
    cashierDb.collection("deliveries").doc("del_pay_1").update({
      paymentCollected: 1000,
      status: "Delivered (Paid)",
    })
  );
});

test("Google-signed-in staff indexed in staffByEmail syncs across phones on Spark plan without custom claims", async () => {
  const GOOGLE_STAFF_EMAIL = "rashid.plant@gmail.com";
  const GOOGLE_STAFF_UID_PHONE_B = "google_auth_uid_phone_b";

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("staffByEmail").doc(GOOGLE_STAFF_EMAIL).set({
      uid: "staff_rashid_generated",
      id: "staff_rashid_generated",
      username: "rashid_gmail",
      name: "Rashid Gmail",
      googleEmail: GOOGLE_STAFF_EMAIL,
      role: "INCHARGE",
      isSuperAdmin: false,
      active: true,
      plantId: PLANT_ID,
      defaultBranchId: PLANT_ID,
      assignedBranchIds: [PLANT_ID],
      permissions: {
        DASHBOARD: ["VIEW"],
        CUSTOMERS: ["VIEW", "ADD"],
        DELIVERIES: ["VIEW", "ADD"],
      },
    });
    await db.collection("customers").doc("cust_shared_1").set({
      id: "cust_shared_1",
      plantId: PLANT_ID,
      name: "Shared Hotel",
      active: true,
      ownerId: ADMIN_UID,
      memberUids: [ADMIN_UID],
      memberEmails: [ADMIN_EMAIL],
    });
    await db.collection("expenses").doc("exp_restricted_1").set({
      id: "exp_restricted_1",
      plantId: PLANT_ID,
      date: "2026-11-18",
      amount: 1200,
      ownerId: ADMIN_UID,
      memberUids: [ADMIN_UID],
    });
  });

  // Google Sign-In user on Phone B with NO custom token claims and UID != staff_rashid_generated
  const googleStaffDb = testEnv
    .authenticatedContext(GOOGLE_STAFF_UID_PHONE_B, {
      email: GOOGLE_STAFF_EMAIL,
      email_verified: true,
    })
    .firestore();

  // Can look up own staffByEmail record and read/create allowed plant collections
  await assertSucceeds(googleStaffDb.collection("staffByEmail").doc(GOOGLE_STAFF_EMAIL).get());
  await assertSucceeds(googleStaffDb.collection("customers").where("plantId", "==", PLANT_ID).get());
  await assertSucceeds(
    googleStaffDb.collection("customers").doc("cust_by_gmail_staff").set({
      id: "cust_by_gmail_staff",
      plantId: PLANT_ID,
      name: "Created by Gmail Staff",
      active: true,
      ownerId: ADMIN_UID,
      memberUids: [ADMIN_UID, GOOGLE_STAFF_UID_PHONE_B],
      memberEmails: [ADMIN_EMAIL, GOOGLE_STAFF_EMAIL],
    })
  );

  // Still blocked from unauthorized modules (EXPENSES) and other plants
  await assertFails(googleStaffDb.collection("expenses").where("plantId", "==", PLANT_ID).get());
  await assertFails(googleStaffDb.collection("staffByEmail").doc("other@gmail.com").get());
  await assertFails(
    googleStaffDb.collection("customers").doc("cust_wrong_plant").set({
      id: "cust_wrong_plant",
      plantId: OTHER_PLANT_ID,
      name: "Wrong Plant",
      active: true,
    })
  );
});

test("Audit Q1 & Legacy Closure: Ordinary users and non-manager staff cannot create/modify staffByEmail or spoof legacy ownership", async () => {
  const GOOGLE_STAFF_EMAIL = "rashid.plant@gmail.com";
  const GOOGLE_STAFF_UID = "google_staff_rashid_uid";

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("staffByEmail").doc(GOOGLE_STAFF_EMAIL).set({
      uid: GOOGLE_STAFF_UID,
      id: GOOGLE_STAFF_UID,
      username: "rashid_gmail",
      name: "Rashid Gmail",
      googleEmail: GOOGLE_STAFF_EMAIL,
      role: "INCHARGE",
      isSuperAdmin: false,
      active: true,
      plantId: PLANT_ID,
      defaultBranchId: PLANT_ID,
      assignedBranchIds: [PLANT_ID],
      permissions: { CUSTOMERS: ["VIEW"], DELIVERIES: ["VIEW", "ADD"] },
    });
  });

  const outsiderDb = testEnv
    .authenticatedContext(OUTSIDER_UID, { email: OUTSIDER_EMAIL, email_verified: true })
    .firestore();
  const staffDb = testEnv
    .authenticatedContext(GOOGLE_STAFF_UID, { email: GOOGLE_STAFF_EMAIL, email_verified: true })
    .firestore();
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();

  // Outsider cannot create or modify own staffByEmail / staffUsers doc
  await assertFails(
    outsiderDb.collection("staffByEmail").doc(OUTSIDER_EMAIL).set({
      id: OUTSIDER_UID,
      uid: OUTSIDER_UID,
      username: "outsider",
      name: "Outsider",
      googleEmail: OUTSIDER_EMAIL,
      role: "ADMIN",
      isSuperAdmin: false,
      active: true,
      plantId: PLANT_ID,
    })
  );
  await assertFails(
    outsiderDb.collection("staffUsers").doc(OUTSIDER_UID).set({
      id: OUTSIDER_UID,
      uid: OUTSIDER_UID,
      username: "outsider",
      name: "Outsider",
      role: "ADMIN",
      active: true,
      plantId: PLANT_ID,
    })
  );

  // Non-manager staff cannot create a second staffByEmail or modify their own staffByEmail
  await assertFails(
    staffDb.collection("staffByEmail").doc(GOOGLE_STAFF_EMAIL).update({ name: "Self Modified" })
  );
  await assertFails(
    staffDb.collection("staffByEmail").doc("friend@gmail.com").set({
      id: "staff_friend",
      uid: "staff_friend",
      username: "friend",
      name: "Friend",
      googleEmail: "friend@gmail.com",
      role: "INCHARGE",
      active: true,
      plantId: PLANT_ID,
    })
  );

  // Outsider CANNOT bypass authorization by putting own UID/email into ownerId/memberUids/memberEmails
  const spoofMetadata = {
    ownerId: OUTSIDER_UID,
    memberUids: [OUTSIDER_UID],
    memberEmails: [OUTSIDER_EMAIL],
    plantId: PLANT_ID,
  };
  await assertFails(
    outsiderDb.collection("customers").doc("spoof_cust").set({
      ...spoofMetadata,
      id: "spoof_cust",
      name: "Spoof Customer",
      active: true,
    })
  );
  await assertFails(
    outsiderDb.collection("payments").doc("spoof_pay").set({
      ...spoofMetadata,
      id: "spoof_pay",
      customerId: "spoof_cust",
      amount: 5000,
    })
  );
  await assertFails(
    outsiderDb.collection("expenses").doc("spoof_exp").set({
      ...spoofMetadata,
      id: "spoof_exp",
      date: "2026-11-18",
      amount: 1000,
    })
  );
  await assertFails(
    outsiderDb.collection("bottleInventory").doc("spoof_inv").set({
      ...spoofMetadata,
      id: "spoof_inv",
      bottleSizeId: "size_19l",
      sizeName: "19L",
      atPlantFull: 100,
    })
  );
  await assertFails(
    outsiderDb.collection("plantWorkspaces").doc("ws_spoof").set({
      ...spoofMetadata,
      id: "ws_spoof",
      plantName: "Spoof Plant",
    })
  );
  await assertFails(
    outsiderDb.collection("passwordResetRequests").doc("req_spoof").set({
      ...spoofMetadata,
      id: "req_spoof",
      username: "admin",
      isResolved: false,
    })
  );
  await assertFails(
    outsiderDb.collection("backups").doc("bak_spoof").set({
      ...spoofMetadata,
      backupId: "bak_spoof",
      totalRecords: 1,
      jsonPayload: "{}",
    })
  );

  // Super Admin mrafiqarain1998@gmail.com CAN create/bootstrap own isSuperAdmin:true profile
  await assertSucceeds(
    adminDb.collection("staffUsers").doc(ADMIN_UID).set({
      id: "user_admin_01",
      uid: ADMIN_UID,
      username: "admin",
      name: "Muhammad Rafiq",
      googleEmail: ADMIN_EMAIL,
      role: "ADMIN",
      isSuperAdmin: true,
      active: true,
      plantId: PLANT_ID,
      assignedBranchIds: [PLANT_ID],
    })
  );
  await assertSucceeds(
    adminDb.collection("staffByEmail").doc(ADMIN_EMAIL).set({
      id: "user_admin_01",
      uid: ADMIN_UID,
      username: "admin",
      name: "Muhammad Rafiq",
      googleEmail: ADMIN_EMAIL,
      role: "ADMIN",
      isSuperAdmin: true,
      active: true,
      plantId: PLANT_ID,
      assignedBranchIds: [PLANT_ID],
    })
  );
});

test("Audit Q2: Staff and delegated managers cannot escalate own role, branches, permissions, active, or Super Admin status", async () => {
  const DELEGATED_UID = "staff_delegated_mgr";
  const DELEGATED_EMAIL = "manager.plant1@gmail.com";

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("staffUsers").doc(STAFF_CUSTOM_UID).set({
      id: STAFF_CUSTOM_UID,
      uid: STAFF_CUSTOM_UID,
      username: "rashid1",
      name: "Rashid",
      role: "INCHARGE",
      isSuperAdmin: false,
      active: true,
      plantId: PLANT_ID,
      defaultBranchId: PLANT_ID,
      assignedBranchIds: [PLANT_ID],
      permissions: { CUSTOMERS: ["VIEW"] },
    });
    await db.collection("staffUsers").doc(DELEGATED_UID).set({
      id: DELEGATED_UID,
      uid: DELEGATED_UID,
      username: "mgr1",
      name: "Plant 1 Manager",
      googleEmail: DELEGATED_EMAIL,
      role: "INCHARGE",
      isSuperAdmin: false,
      active: true,
      plantId: PLANT_ID,
      defaultBranchId: PLANT_ID,
      assignedBranchIds: [PLANT_ID],
      permissions: { STAFF_MANAGEMENT: ["VIEW", "MANAGE"], CUSTOMERS: ["VIEW"] },
    });
    await db.collection("staffByEmail").doc(DELEGATED_EMAIL).set({
      id: DELEGATED_UID,
      uid: DELEGATED_UID,
      username: "mgr1",
      name: "Plant 1 Manager",
      googleEmail: DELEGATED_EMAIL,
      role: "INCHARGE",
      isSuperAdmin: false,
      active: true,
      plantId: PLANT_ID,
      defaultBranchId: PLANT_ID,
      assignedBranchIds: [PLANT_ID],
      permissions: { STAFF_MANAGEMENT: ["VIEW", "MANAGE"], CUSTOMERS: ["VIEW"] },
    });
    await db.collection("staffUsers").doc("other_branch_staff").set({
      id: "other_branch_staff",
      uid: "other_branch_staff",
      username: "lahore_op",
      name: "Lahore Operator",
      role: "INCHARGE",
      isSuperAdmin: false,
      active: true,
      plantId: OTHER_PLANT_ID,
      defaultBranchId: OTHER_PLANT_ID,
      assignedBranchIds: [OTHER_PLANT_ID],
      permissions: { CUSTOMERS: ["VIEW"] },
    });
  });

  const staffDb = testEnv
    .authenticatedContext(STAFF_CUSTOM_UID, { appStaff: true, plantId: PLANT_ID })
    .firestore();
  const mgrDb = testEnv
    .authenticatedContext(DELEGATED_UID, { email: DELEGATED_EMAIL, email_verified: true })
    .firestore();

  // Ordinary staff cannot alter any privilege field on self
  await assertFails(staffDb.collection("staffUsers").doc(STAFF_CUSTOM_UID).update({ role: "ADMIN" }));
  await assertFails(staffDb.collection("staffUsers").doc(STAFF_CUSTOM_UID).update({ isSuperAdmin: true }));
  await assertFails(staffDb.collection("staffUsers").doc(STAFF_CUSTOM_UID).update({ plantId: OTHER_PLANT_ID }));
  await assertFails(staffDb.collection("staffUsers").doc(STAFF_CUSTOM_UID).update({ assignedBranchIds: [PLANT_ID, OTHER_PLANT_ID] }));
  await assertFails(staffDb.collection("staffUsers").doc(STAFF_CUSTOM_UID).update({ permissions: { EXPENSES: ["VIEW", "DELETE"] } }));
  await assertFails(staffDb.collection("staffUsers").doc(STAFF_CUSTOM_UID).update({ active: false }));

  // Delegated manager cannot escalate own role/permissions/branches/isSuperAdmin
  await assertFails(mgrDb.collection("staffUsers").doc(DELEGATED_UID).update({ isSuperAdmin: true }));
  await assertFails(mgrDb.collection("staffUsers").doc(DELEGATED_UID).update({ role: "ADMIN" }));
  await assertFails(mgrDb.collection("staffUsers").doc(DELEGATED_UID).update({ assignedBranchIds: [PLANT_ID, OTHER_PLANT_ID] }));
  await assertFails(mgrDb.collection("staffUsers").doc(DELEGATED_UID).update({ plantId: OTHER_PLANT_ID }));
  await assertFails(mgrDb.collection("staffUsers").doc(DELEGATED_UID).update({ permissions: { SETTINGS: ["MANAGE"] } }));
  await assertFails(mgrDb.collection("staffByEmail").doc(DELEGATED_EMAIL).update({ isSuperAdmin: true }));

  // Delegated manager cannot escalate another staff member to isSuperAdmin, ADMIN, empty branches, or another plant
  await assertFails(mgrDb.collection("staffUsers").doc(STAFF_CUSTOM_UID).update({ isSuperAdmin: true }));
  await assertFails(mgrDb.collection("staffUsers").doc(STAFF_CUSTOM_UID).update({ role: "ADMIN" }));
  await assertFails(mgrDb.collection("staffUsers").doc(STAFF_CUSTOM_UID).update({ assignedBranchIds: [] }));
  await assertFails(mgrDb.collection("staffUsers").doc(STAFF_CUSTOM_UID).update({ assignedBranchIds: [OTHER_PLANT_ID] }));
  await assertFails(mgrDb.collection("staffUsers").doc("other_branch_staff").update({ name: "Hijacked" }));

  // Delegated manager CAN legitimately update name/phone/incharge-permissions for same-plant staff
  await assertSucceeds(
    mgrDb.collection("staffUsers").doc(STAFF_CUSTOM_UID).update({
      name: "Rashid Updated",
      permissions: { CUSTOMERS: ["VIEW", "ADD"] },
    })
  );
});

test("Audit Q3 & Q4: Staff cannot read other staff profiles or enumerate staffByEmail; non-existent own email leaks nothing", async () => {
  const STAFF_A_UID = "staff_a_uid";
  const STAFF_B_UID = "staff_b_uid";
  const STAFF_A_EMAIL = "staffa@gmail.com";
  const STAFF_B_EMAIL = "staffb@gmail.com";

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("staffUsers").doc(STAFF_A_UID).set({
      id: STAFF_A_UID,
      uid: STAFF_A_UID,
      username: "staffa",
      name: "Staff A",
      phone: "03001111111",
      googleEmail: STAFF_A_EMAIL,
      role: "INCHARGE",
      isSuperAdmin: false,
      active: true,
      plantId: PLANT_ID,
      assignedBranchIds: [PLANT_ID],
      permissions: { CUSTOMERS: ["VIEW"] },
    });
    await db.collection("staffByEmail").doc(STAFF_A_EMAIL).set({
      id: STAFF_A_UID,
      uid: STAFF_A_UID,
      username: "staffa",
      name: "Staff A",
      phone: "03001111111",
      googleEmail: STAFF_A_EMAIL,
      role: "INCHARGE",
      isSuperAdmin: false,
      active: true,
      plantId: PLANT_ID,
      assignedBranchIds: [PLANT_ID],
      permissions: { CUSTOMERS: ["VIEW"] },
    });
    await db.collection("staffUsers").doc(STAFF_B_UID).set({
      id: STAFF_B_UID,
      uid: STAFF_B_UID,
      username: "staffb",
      name: "Staff B",
      phone: "03009999999",
      googleEmail: STAFF_B_EMAIL,
      role: "ADMIN",
      isSuperAdmin: false,
      active: true,
      plantId: PLANT_ID,
      assignedBranchIds: [PLANT_ID],
      permissions: { SETTINGS: ["MANAGE"] },
    });
    await db.collection("staffByEmail").doc(STAFF_B_EMAIL).set({
      id: STAFF_B_UID,
      uid: STAFF_B_UID,
      username: "staffb",
      name: "Staff B",
      phone: "03009999999",
      googleEmail: STAFF_B_EMAIL,
      role: "ADMIN",
      isSuperAdmin: false,
      active: true,
      plantId: PLANT_ID,
      assignedBranchIds: [PLANT_ID],
      permissions: { SETTINGS: ["MANAGE"] },
    });
  });

  const staffADb = testEnv
    .authenticatedContext(STAFF_A_UID, { email: STAFF_A_EMAIL, email_verified: true })
    .firestore();
  const outsiderDb = testEnv
    .authenticatedContext(OUTSIDER_UID, { email: OUTSIDER_EMAIL, email_verified: true })
    .firestore();

  // Staff A can get OWN profile
  await assertSucceeds(staffADb.collection("staffUsers").doc(STAFF_A_UID).get());
  await assertSucceeds(staffADb.collection("staffByEmail").doc(STAFF_A_EMAIL).get());

  // Staff A CANNOT get Staff B's profile or list staffUsers / staffByEmail / users
  await assertFails(staffADb.collection("staffUsers").doc(STAFF_B_UID).get());
  await assertFails(staffADb.collection("staffByEmail").doc(STAFF_B_EMAIL).get());
  await assertFails(staffADb.collection("staffUsers").get());
  await assertFails(staffADb.collection("staffUsers").where("plantId", "==", PLANT_ID).get());
  await assertFails(staffADb.collection("staffByEmail").get());
  await assertFails(staffADb.collection("users").where("plantId", "==", PLANT_ID).get());

  // Outsider cannot enumerate or look up any staff profile by email
  await assertFails(outsiderDb.collection("staffByEmail").get());
  await assertFails(outsiderDb.collection("staffByEmail").doc(STAFF_A_EMAIL).get());
  await assertFails(outsiderDb.collection("staffByEmail").doc(STAFF_B_EMAIL).get());
  await assertFails(outsiderDb.collection("staffUsers").get());
  const ownEmptyLookup = await assertSucceeds(outsiderDb.collection("staffByEmail").doc(OUTSIDER_EMAIL).get());
  if (ownEmptyLookup.exists) {
    throw new Error("Expected non-existent own staffByEmail lookup for outsider");
  }
});

test("Audit Q5: Unauthorized users and restricted staff cannot access financial, payment, expense, stock, or audit collections", async () => {
  const DELIVERY_ONLY_UID = "staff_delivery_only";
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("staffUsers").doc(DELIVERY_ONLY_UID).set({
      id: DELIVERY_ONLY_UID,
      uid: DELIVERY_ONLY_UID,
      username: "driver1",
      name: "Driver",
      role: "INCHARGE",
      isSuperAdmin: false,
      active: true,
      plantId: PLANT_ID,
      assignedBranchIds: [PLANT_ID],
      permissions: { DELIVERIES: ["VIEW", "ADD"] },
    });
    await db.collection("customers").doc("cust_fin").set({ id: "cust_fin", plantId: PLANT_ID, name: "VIP", active: true });
    await db.collection("payments").doc("pay_fin").set({ id: "pay_fin", plantId: PLANT_ID, customerId: "cust_fin", amount: 5000 });
    await db.collection("expenses").doc("exp_fin").set({ id: "exp_fin", plantId: PLANT_ID, date: "2026-11-18", amount: 2500 });
    await db.collection("bottleInventory").doc("inv_fin").set({ id: "inv_fin", plantId: PLANT_ID, bottleSizeId: "s1", sizeName: "19L", atPlantFull: 50 });
    await db.collection("cashClosings").doc("cc_fin").set({ id: "cc_fin", plantId: PLANT_ID, date: "2026-11-18", netCash: 10000 });
    await db.collection("auditLogs").doc("log_fin").set({
      id: "log_fin",
      plantId: PLANT_ID,
      actorUid: ADMIN_UID,
      action: "CREATE",
      module: "PAYMENTS_BALANCES",
      timestampMillis: 1700000000000,
    });
  });

  const driverDb = testEnv
    .authenticatedContext(DELIVERY_ONLY_UID, { appStaff: true, plantId: PLANT_ID })
    .firestore();
  const outsiderDb = testEnv
    .authenticatedContext(OUTSIDER_UID, { email: OUTSIDER_EMAIL, email_verified: true })
    .firestore();

  // Driver with ONLY DELIVERIES permission is blocked from customers, payments, expenses, stock, cashClosings, auditLogs
  await assertFails(driverDb.collection("customers").doc("cust_fin").get());
  await assertFails(driverDb.collection("payments").doc("pay_fin").get());
  await assertFails(driverDb.collection("expenses").doc("exp_fin").get());
  await assertFails(driverDb.collection("bottleInventory").doc("inv_fin").get());
  await assertFails(driverDb.collection("cashClosings").doc("cc_fin").get());
  await assertFails(driverDb.collection("auditLogs").doc("log_fin").get());

  // Outsider is blocked from all financial & operational collections
  await assertFails(outsiderDb.collection("customers").doc("cust_fin").get());
  await assertFails(outsiderDb.collection("payments").doc("pay_fin").get());
  await assertFails(outsiderDb.collection("expenses").doc("exp_fin").get());
  await assertFails(outsiderDb.collection("bottleInventory").doc("inv_fin").get());
  await assertFails(outsiderDb.collection("cashClosings").doc("cc_fin").get());
  await assertFails(outsiderDb.collection("auditLogs").doc("log_fin").get());
});

test("End-to-end Delivery, Pending Balance, Payment, Expense, Stock & Material sync for Google-signed-in Incharge staff (no expression-limit errors)", async () => {
  const INCHARGE_EMAIL = "incharge.plant@gmail.com";
  const INCHARGE_UID = "google_incharge_uid_01";

  const defaultInchargePermissions = {
    DASHBOARD: ["VIEW"],
    DELIVERIES: ["VIEW", "ADD"],
    CUSTOMERS: ["VIEW", "ADD", "EDIT"],
    PAYMENTS_BALANCES: ["VIEW", "ADD"],
    BOTTLE_STOCK: ["VIEW", "ADD"],
    BOTTLE_ISSUE: ["VIEW", "ADD"],
    REFILL_BATCHES: ["VIEW", "ADD"],
    EXPENSES: ["VIEW", "ADD"],
    FACTORY_MATERIALS: ["VIEW", "ADD"],
    STAFF_MANAGEMENT: [],
    REPORTS: ["VIEW"],
    SETTINGS: [],
    AUDIT_HISTORY: [],
  };

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("staffByEmail").doc(INCHARGE_EMAIL).set({
      id: "staff_incharge_01",
      uid: "staff_incharge_01",
      username: "incharge1",
      name: "Ali Incharge",
      googleEmail: INCHARGE_EMAIL,
      role: "INCHARGE",
      isSuperAdmin: false,
      active: true,
      plantId: PLANT_ID,
      defaultBranchId: PLANT_ID,
      assignedBranchIds: [PLANT_ID],
      permissions: defaultInchargePermissions,
    });
    await db.collection("bottleSizes").doc("size_19l").set({
      id: "size_19l",
      plantId: PLANT_ID,
      name: "19L Bottle",
      capacityLitres: 19,
      defaultRefillPrice: 120,
      active: true,
      ownerId: ADMIN_UID,
      memberUids: [ADMIN_UID],
    });
    await db.collection("factoryMaterials").doc("mat_caps").set({
      id: "mat_caps",
      plantId: PLANT_ID,
      name: "19L Caps",
      currentStock: 500,
      ownerId: ADMIN_UID,
      memberUids: [ADMIN_UID],
    });
  });

  const staffDb = testEnv
    .authenticatedContext(INCHARGE_UID, { email: INCHARGE_EMAIL, email_verified: true })
    .firestore();

  const meta = {
    plantId: PLANT_ID,
    ownerId: ADMIN_UID,
    memberUids: [ADMIN_UID, INCHARGE_UID],
    memberEmails: [ADMIN_EMAIL, INCHARGE_EMAIL],
  };

  // 1. Add customer + initial bottle allocation
  await assertSucceeds(
    staffDb.collection("customers").doc("cust_e2e_1").set({
      ...meta,
      id: "cust_e2e_1",
      name: "Al-Noor Mart",
      active: true,
      currentBalance: 0,
      emptyBottlesOwed: 0,
      totalPurchases: 0,
    })
  );
  await assertSucceeds(
    staffDb.collection("customerBottleAllocations").doc("alloc_e2e_1").set({
      ...meta,
      id: "alloc_e2e_1",
      customerId: "cust_e2e_1",
      bottleSizeId: "size_19l",
      emptyHeld: 0,
      totalIssued: 0,
    })
  );

  // 2. Full recordDelivery flow across ALL touched collections
  await assertSucceeds(
    staffDb.collection("deliveries").doc("del_e2e_1").set({
      ...meta,
      id: "del_e2e_1",
      deliveryNumber: "DEL-1001",
      customerId: "cust_e2e_1",
      customerName: "Al-Noor Mart",
      date: "2026-11-18",
      totalAmount: 1200,
      paymentCollected: 500,
      status: "Delivered (Partial)",
    })
  );
  await assertSucceeds(
    staffDb.collection("invoices").doc("inv_e2e_1").set({
      ...meta,
      id: "inv_e2e_1",
      invoiceNumber: "INV-1001",
      customerId: "cust_e2e_1",
      totalAmount: 1200,
      amountPaid: 500,
      balanceDue: 700,
      status: "PARTIAL",
    })
  );
  // Customer pending balance + empty bottles delta update (previously hit 1000-expression limit!)
  await assertSucceeds(
    staffDb.collection("customers").doc("cust_e2e_1").update({
      currentBalance: 700,
      emptyBottlesOwed: 10,
      totalPurchases: 1200,
      lastDeliveryDate: "2026-11-18",
    })
  );
  // Bottle inventory create fallback + update
  await assertSucceeds(
    staffDb.collection("bottleInventory").doc("inv_size_19l").set({
      ...meta,
      id: "inv_size_19l",
      bottleSizeId: "size_19l",
      sizeName: "19L Bottle",
      atPlantFull: 90,
      atPlantEmpty: 10,
      inUse: 0,
      withCustomers: 10,
      damaged: 0,
    })
  );
  await assertSucceeds(
    staffDb.collection("bottleInventory").doc("inv_size_19l").update({
      atPlantFull: 85,
      withCustomers: 15,
    })
  );
  // Bottle movement + issue sale + delivery payment + customer allocation update + audit log
  await assertSucceeds(
    staffDb.collection("bottleMovements").doc("mov_e2e_1").set({
      ...meta,
      id: "mov_e2e_1",
      bottleSizeId: "size_19l",
      date: "2026-11-18",
      notes: "Delivery DEL-1001",
    })
  );
  await assertSucceeds(
    staffDb.collection("bottleIssueSales").doc("bis_e2e_1").set({
      ...meta,
      id: "bis_e2e_1",
      bottleSizeId: "size_19l",
      date: "2026-11-18",
      notes: "Issued with delivery",
    })
  );
  await assertSucceeds(
    staffDb.collection("customerBottleAllocations").doc("alloc_e2e_1").update({
      emptyHeld: 10,
      totalIssued: 10,
    })
  );
  await assertSucceeds(
    staffDb.collection("payments").doc("pay_e2e_1").set({
      ...meta,
      id: "pay_e2e_1",
      receiptNumber: "RCP-1001",
      customerId: "cust_e2e_1",
      customerName: "Al-Noor Mart",
      amount: 500,
      date: "2026-11-18",
    })
  );
  await assertSucceeds(
    staffDb.collection("auditLogs").doc("audit_e2e_1").set({
      id: "audit_e2e_1",
      plantId: PLANT_ID,
      actorUid: INCHARGE_UID,
      actorUsername: "incharge1",
      actorName: "Ali Incharge",
      actorRole: "INCHARGE",
      action: "CREATE",
      module: "DELIVERIES",
      recordId: "del_e2e_1",
      timestampMillis: Date.now(),
    })
  );

  // 3. Record subsequent payment updating invoice + delivery + customer pending amount
  await assertSucceeds(
    staffDb.collection("payments").doc("pay_e2e_2").set({
      ...meta,
      id: "pay_e2e_2",
      receiptNumber: "RCP-1002",
      customerId: "cust_e2e_1",
      customerName: "Al-Noor Mart",
      amount: 700,
      date: "2026-11-18",
    })
  );
  await assertSucceeds(
    staffDb.collection("invoices").doc("inv_e2e_1").update({
      amountPaid: 1200,
      balanceDue: 0,
      status: "PAID",
    })
  );
  await assertSucceeds(
    staffDb.collection("deliveries").doc("del_e2e_1").update({
      paymentCollected: 1200,
      status: "Delivered (Paid)",
    })
  );
  await assertSucceeds(
    staffDb.collection("customers").doc("cust_e2e_1").update({
      currentBalance: 0,
    })
  );

  // 4. Add expense + refill factory material + refill bottle stock
  await assertSucceeds(
    staffDb.collection("expenses").doc("exp_e2e_1").set({
      ...meta,
      id: "exp_e2e_1",
      voucherNumber: "EXP-1001",
      categoryName: "Electricity",
      date: "2026-11-18",
      amount: 4500,
      description: "Plant electricity bill",
    })
  );
  await assertSucceeds(
    staffDb.collection("factoryMaterials").doc("mat_caps").update({
      currentStock: 1000,
    })
  );
  await assertSucceeds(
    staffDb.collection("materialTransactions").doc("mtx_e2e_1").set({
      ...meta,
      id: "mtx_e2e_1",
      name: "19L Caps",
      notes: "Refill stock",
    })
  );
  await assertSucceeds(
    staffDb.collection("bottleSizes").doc("size_19l").update({
      defaultRefillPrice: 130,
    })
  );
  await assertSucceeds(
    staffDb.collection("bottleRefills").doc("ref_e2e_1").set({
      ...meta,
      id: "ref_e2e_1",
      bottleSizeId: "size_19l",
      date: "2026-11-18",
    })
  );
});

test("appReleases/latest allows Super Admin HTTPS publish and signed-in get while blocking non-admin writes and list", async () => {
  const superDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();
  const staffDb = testEnv
    .authenticatedContext("staff_reader_1", { email: "staff.reader@gmail.com", email_verified: true })
    .firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();

  await assertSucceeds(
    superDb.collection("appReleases").doc("latest").set({
      versionCode: 102,
      versionName: "1.0.2",
      downloadUrl: "https://github.com/mrafiqarain1998/aab-e-baraan/releases/latest/download/app-debug.apk",
      githubRepo: "mrafiqarain1998/aab-e-baraan",
      releaseNotes: "Sync optimization and free OTA update support",
      mandatory: false,
      updatedAtMillis: 1700000000000,
    })
  );

  await assertFails(
    superDb.collection("appReleases").doc("latest").set({
      versionCode: 103,
      versionName: "1.0.3",
      downloadUrl: "http://insecure.example.com/app.apk",
    })
  );

  await assertSucceeds(staffDb.collection("appReleases").doc("latest").get());
  await assertFails(unauthDb.collection("appReleases").doc("latest").get());
  await assertFails(staffDb.collection("appReleases").get());
  await assertFails(
    staffDb.collection("appReleases").doc("latest").update({ versionCode: 999 })
  );
  await assertFails(
    staffDb.collection("appReleases").doc("latest").delete()
  );
});



