const { initializeApp, getApps } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { onRequest } = require("firebase-functions/v2/https");
const { DATABASE_ID, createAuthCore } = require("./src/authCore");

const app = getApps().length > 0 ? getApps()[0] : initializeApp();

// Strictly bind Firebase Admin SDK to the named Firestore Enterprise (Native) database.
// Never fall back to (default).
const db = getFirestore(app, DATABASE_ID);
const auth = getAuth(app);

const core = createAuthCore({
  db,
  auth,
  serverTimestampFn: () => FieldValue.serverTimestamp(),
  nowFn: () => Date.now(),
});

exports.api = onRequest(
  {
    region: "us-central1",
    cors: true,
    maxInstances: 10,
  },
  async (req, res) => {
    try {
      const forwarded = String(req.headers["x-forwarded-for"] || "").split(",")[0].trim();
      const remoteIp = forwarded || req.ip || "unknown";
      const parsedBody =
        typeof req.body === "string" && req.body.trim().startsWith("{")
          ? JSON.parse(req.body)
          : req.body || {};

      const result = await core.dispatch(
        req.method,
        req.path || req.url || "/",
        req.headers || {},
        parsedBody,
        remoteIp
      );
      res.status(result.status).json(result.body);
    } catch (err) {
      res.status(500).json({
        ok: false,
        errorCode: "INTERNAL_ERROR",
        message: err && err.message ? err.message : "Internal backend error",
      });
    }
  }
);
