"use strict";

/* =====================================================
   SCANX front end
   Talks to the Java backend over the JSON API below.
   ===================================================== */

const API_BASE = (function () {
    const path = location.pathname;
    const dir = path.endsWith("/") ? path : path.replace(/[^/]+$/, "");
    return dir + "api/";
})();

const TOKEN_KEY = "scanx.token";
const ROLE_KEY = "scanx.role";
const STUDENT_KEY = "scanx.studentId";


/* ---------- token storage: localStorage (Remember me) or sessionStorage ---------- */

function sxGetToken() {
    return localStorage.getItem(TOKEN_KEY) || sessionStorage.getItem(TOKEN_KEY);
}

function sxSaveLogin(token, role, studentId, remember) {
    const store = remember ? localStorage : sessionStorage;
    const other = remember ? sessionStorage : localStorage;
    store.setItem(TOKEN_KEY, token);
    store.setItem(ROLE_KEY, role);
    if (studentId) {
        store.setItem(STUDENT_KEY, studentId);
    } else {
        store.removeItem(STUDENT_KEY);
    }
    other.removeItem(TOKEN_KEY);
    other.removeItem(ROLE_KEY);
    other.removeItem(STUDENT_KEY);
}

function sxClearLogin() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(ROLE_KEY);
    localStorage.removeItem(STUDENT_KEY);
    sessionStorage.removeItem(TOKEN_KEY);
    sessionStorage.removeItem(ROLE_KEY);
    sessionStorage.removeItem(STUDENT_KEY);
}

function sxGetRole() {
    return localStorage.getItem(ROLE_KEY) || sessionStorage.getItem(ROLE_KEY);
}


/* ---------- the one function everything talks to the server through ---------- */

class ApiError extends Error {

    constructor(message, code, status) {

        super(message);

        this.code = code;

        this.status = status;

    }

}


async function api(path, options) {

    const opts = options || {};

    const headers = { "Content-Type": "application/json" };

    const token = sxGetToken();

    if (token) {

        headers.Authorization = "Bearer " + token;

    }

    let response;

    try {

        response = await fetch(API_BASE + path, {
            method: opts.method || "GET",
            headers: headers,
            body: opts.body === undefined ? undefined : JSON.stringify(opts.body)
        });

    } catch (networkError) {

        throw new ApiError(
            "Could not reach the SCANX server. Check that it is running and try again.",
            "OFFLINE",
            0
        );

    }

    let data = {};

    try {

        const text = await response.text();

        if (text) {

            data = JSON.parse(text);

        }

    } catch (parseError) {

        // leave data as {}

    }

    if (response.status === 401 && path !== "auth/login") {

        sxSessionExpired();

    }

    if (!response.ok) {

        throw new ApiError(
            data.message || "Something went wrong on the server.",
            data.code || "ERROR",
            response.status
        );

    }

    return data;

}


function sxSessionExpired() {

    if (sxGetToken()) {

        sxClearLogin();

        sxToast("Your session ended. Please sign in again.", "warn");

        setTimeout(function () {

            location.reload();

        }, 900);

    }

}


/* ---------- small formatting helpers ---------- */

function $(id) {

    return document.getElementById(id);

}


function sxEl(tag, props, children) {

    const element = document.createElement(tag);

    Object.keys(props || {}).forEach(function (key) {

        const value = props[key];

        if (value === null || value === undefined || value === false) {

            return;

        }

        if (key === "class") {

            element.className = value;

        } else if (key === "text") {

            element.textContent = value;

        } else if (key === "style") {

            element.setAttribute("style", value);

        } else if (key.slice(0, 2) === "on" && typeof value === "function") {

            element.addEventListener(key.slice(2), value);

        } else if (value === true) {

            element.setAttribute(key, "");

        } else {

            element.setAttribute(key, value);

        }

    });

    (children || []).forEach(function (child) {

        if (child === null || child === undefined || child === false) {

            return;

        }

        element.appendChild(
            typeof child === "string" ? document.createTextNode(child) : child
        );

    });

    return element;

}


function sxClear(element) {

    if (element) {

        element.textContent = "";

    }

}


function sxSetText(id, text) {

    const element = $(id);

    if (element) {

        element.textContent = text;

    }

}


function sxToast(message, type) {

    let stack = $("sxToastStack");

    if (!stack) {

        return;

    }

    const toast = sxEl("div", {
        class: "sx-toast sx-toast-" + (type || "info"),
        role: "status",
        text: message
    });

    stack.appendChild(toast);

    setTimeout(function () {

        toast.classList.add("sx-toast-out");

        setTimeout(function () { toast.remove(); }, 250);

    }, 3800);

}


const SX_MONTHS_SHORT = ["JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"];

const SX_MONTHS_LONG = ["January", "February", "March", "April", "May", "June", "July", "August",
    "September", "October", "November", "December"];

const SX_WEEKDAY_NAMES = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"];

function sxPad(n) {

    return String(n).padStart(2, "0");

}

function sxDateKey(date) {

    const d = date || new Date();

    return d.getFullYear() + "-" + sxPad(d.getMonth() + 1) + "-" + sxPad(d.getDate());

}

function sxParseDateKey(key) {

    if (!key) {

        return null;

    }

    const parts = key.split("-");

    if (parts.length !== 3) {

        return null;

    }

    const date = new Date(Number(parts[0]), Number(parts[1]) - 1, Number(parts[2]));

    return isNaN(date.getTime()) ? null : date;

}

function sxAddDays(date, days) {

    const copy = new Date(date.getTime());

    copy.setDate(copy.getDate() + days);

    return copy;

}

function sxFormatDateLong(key) {

    const date = sxParseDateKey(key);

    if (!date) {

        return "";

    }

    return date.getDate() + " " + SX_MONTHS_LONG[date.getMonth()] + " " + date.getFullYear();

}

function sxFormatTime(iso) {

    if (!iso) {

        return "—";

    }

    return new Date(iso).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });

}

function sxFormatDuration(ms) {

    if (!ms || ms <= 0) {

        return "0m";

    }

    const totalMinutes = Math.floor(ms / 60000);

    if (totalMinutes < 1) {

        return "<1m";

    }

    const hours = Math.floor(totalMinutes / 60);

    const minutes = totalMinutes % 60;

    return hours === 0 ? minutes + "m" : hours + "h " + sxPad(minutes) + "m";

}

function sxRelativeDay(dateKey) {

    const date = sxParseDateKey(dateKey);

    if (!date) {

        return "No date";

    }

    const days = Math.round((date.getTime() - sxStartOfDay(new Date()).getTime()) / 86400000);

    if (days === 0) return "Today";

    if (days === 1) return "Tomorrow";

    if (days === -1) return "Yesterday";

    return date.getDate() + " " + SX_MONTHS_SHORT[date.getMonth()].charAt(0)
        + SX_MONTHS_SHORT[date.getMonth()].slice(1).toLowerCase();

}

function sxStartOfDay(date) {

    return new Date(date.getFullYear(), date.getMonth(), date.getDate());

}

function sxFormatClock(time24) {

    if (!time24) {

        return "";

    }

    const parts = time24.split(":");

    const hours = Number(parts[0]);

    const minutes = Number(parts[1] || 0);

    if (isNaN(hours) || isNaN(minutes)) {

        return "";

    }

    const suffix = hours >= 12 ? "PM" : "AM";

    const twelve = hours % 12 === 0 ? 12 : hours % 12;

    return twelve + ":" + sxPad(minutes) + " " + suffix;

}

function sxPercentText(value) {

    return value === null || value === undefined ? "—" : Math.round(value) + "%";

}

function sxFillSelect(select, placeholder, options) {

    sxClear(select);

    select.appendChild(sxEl("option", { value: "", text: placeholder }));

    options.forEach(function (opt) {

        select.appendChild(sxEl("option", { value: opt.value, text: opt.text }));

    });

}


/* =====================================================
   CAMERA POP-UP (face enrolment / face check)
   ===================================================== */

const SxModal = {

    onClose: null,

    open: function (config) {

        const modal = $("sxCameraModal");

        if (!modal) {

            return null;

        }

        SxModal.onClose = config.onClose || null;

        sxSetText("sxModalLabel", config.label || "");

        sxSetText("sxModalTitle", config.title || "");

        SxModal.setStatus(config.status || "", "info");

        SxModal.setActions(config.actions || []);

        modal.hidden = false;

        document.body.classList.add("sx-modal-open");

        const close = $("sxModalClose");

        if (close) {

            close.focus();

        }

        return $("sxModalVideo");

    },

    setStatus: function (text, type) {

        const status = $("sxModalStatus");

        if (!status) {

            return;

        }

        status.textContent = text;

        status.className = "sx-modal-status sx-status-" + (type || "info");

    },

    setActions: function (actions) {

        const holder = $("sxModalActions");

        sxClear(holder);

        actions.forEach(function (action) {

            const button = sxEl("button", {
                type: "button",
                class: "sx-btn " + (action.ghost ? "sx-btn-ghost" : ""),
                text: action.text
            });

            button.disabled = !!action.disabled;

            button.addEventListener("click", action.onClick);

            holder.appendChild(button);

        });

    },

    close: function () {

        const modal = $("sxCameraModal");

        if (!modal || modal.hidden) {

            return;

        }

        modal.hidden = true;

        document.body.classList.remove("sx-modal-open");

        const callback = SxModal.onClose;

        SxModal.onClose = null;

        if (callback) {

            callback();

        }

    }

};


document.addEventListener("keydown", function (event) {

    if (event.key === "Escape") {

        SxModal.close();

    }

});

document.addEventListener("click", function (event) {

    if (event.target && event.target.id === "sxModalClose") {

        SxModal.close();

    }

    if (event.target && event.target.id === "sxCameraModal") {

        SxModal.close();

    }

});


/* =====================================================
   CAMERA + SCANNING ENGINES
   QR / barcode  → BarcodeDetector (built into Chrome, Edge, Android) or jsQR fallback
   Face          → face-api.js (loaded only when needed)
   Libraries load from a CDN the first time they are used,
   so the very first scan needs an internet connection.
   ===================================================== */

const SX_JSQR_SCRIPT = "https://cdnjs.cloudflare.com/ajax/libs/jsQR/1.4.0/jsQR.min.js";
const SX_QRCODE_SCRIPT = "https://cdnjs.cloudflare.com/ajax/libs/qrcode-generator/1.4.4/qrcode.min.js";
const SX_FACEAPI_SCRIPT = "https://cdn.jsdelivr.net/npm/@vladmandic/face-api@1/dist/face-api.js";
const SX_FACEAPI_MODELS = "https://cdn.jsdelivr.net/npm/@vladmandic/face-api@1/model/";


function sxSleep(ms) {

    return new Promise(function (resolve) { setTimeout(resolve, ms); });

}


function sxLoadScript(src) {

    return new Promise(function (resolve, reject) {

        const existing = document.querySelector('script[data-sx-src="' + src + '"]');

        if (existing) {

            if (existing.dataset.loaded) { resolve(); return; }

            existing.addEventListener("load", function () { resolve(); });

            existing.addEventListener("error", function () { reject(new Error("Could not load " + src)); });

            return;

        }

        const script = document.createElement("script");

        script.src = src;

        script.async = true;

        script.dataset.sxSrc = src;

        script.onload = function () { script.dataset.loaded = "1"; resolve(); };

        script.onerror = function () { script.remove(); reject(new Error("Could not load " + src)); };

        document.head.appendChild(script);

    });

}


function sxCameraErrorMessage(error) {

    const name = error && error.name;

    if (name === "NotAllowedError" || name === "SecurityError") {

        return "Camera access was blocked. Allow the camera for this site and try again.";

    }

    if (name === "NotFoundError" || name === "OverconstrainedError") {

        return "No camera was found on this device.";

    }

    if (name === "NotReadableError") {

        return "The camera is being used by another app. Close it and try again.";

    }

    return (error && error.message) || "The camera could not start.";

}


function sxCreateCamera(video) {

    let stream = null;

    function stopStream() {

        if (stream) {

            stream.getTracks().forEach(function (track) { track.stop(); });

            stream = null;

        }

        video.srcObject = null;

    }

    return {

        start: async function (facing) {

            stopStream();

            if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {

                throw new Error("The camera needs HTTPS or localhost. Open the site from a secure address.");

            }

            stream = await navigator.mediaDevices.getUserMedia({
                video: { facingMode: facing || "environment", width: { ideal: 1280 }, height: { ideal: 720 } },
                audio: false
            });

            video.setAttribute("playsinline", "");

            video.muted = true;

            video.srcObject = stream;

            await video.play();

        },

        stop: stopStream

    };

}


async function sxCreateBarcodeDetector(wantedFormats) {

    if (!("BarcodeDetector" in window)) {

        return null;

    }

    try {

        const supported = await window.BarcodeDetector.getSupportedFormats();

        const formats = wantedFormats.filter(function (f) { return supported.indexOf(f) !== -1; });

        return formats.length ? new window.BarcodeDetector({ formats: formats }) : null;

    } catch (error) {

        return null;

    }

}


const SX_QR_FORMATS = ["qr_code"];

const SX_ID_FORMATS = ["qr_code", "code_128", "code_39", "code_93", "codabar",
    "ean_13", "ean_8", "itf", "upc_a", "upc_e", "data_matrix", "pdf417"];


function sxStartCodeLoop(video, wantedFormats, onCode) {

    let running = true;

    let timer = null;

    let detector = null;

    let canvas = null;

    let context = null;

    async function prepare() {

        detector = await sxCreateBarcodeDetector(wantedFormats);

        if (!detector) {

            if (!window.jsQR) {

                await sxLoadScript(SX_JSQR_SCRIPT);

            }

            canvas = document.createElement("canvas");

            context = canvas.getContext("2d", { willReadFrequently: true });

        }

    }

    async function readFrame() {

        if (detector) {

            const codes = await detector.detect(video);

            return codes.length ? codes[0].rawValue : null;

        }

        const width = video.videoWidth;

        const height = video.videoHeight;

        if (!width || !height) {

            return null;

        }

        const scale = Math.min(1, 640 / width);

        canvas.width = Math.round(width * scale);

        canvas.height = Math.round(height * scale);

        context.drawImage(video, 0, 0, canvas.width, canvas.height);

        const image = context.getImageData(0, 0, canvas.width, canvas.height);

        const result = window.jsQR(image.data, image.width, image.height, { inversionAttempts: "dontInvert" });

        return result ? result.data : null;

    }

    async function tick() {

        if (!running) {

            return;

        }

        try {

            if (video.readyState >= 2) {

                const text = await readFrame();

                if (text && running) {

                    await onCode(text);

                }

            }

        } catch (error) {

            console.warn("SCANX code scan:", error);

        }

        if (running) {

            timer = setTimeout(tick, 250);

        }

    }

    const ready = prepare().then(function () {

        if (running) {

            tick();

        }

        return { fallback: !detector };

    });

    return {

        ready: ready,

        stop: function () { running = false; clearTimeout(timer); }

    };

}


let sxFacePromise = null;

function sxEnsureFaceApi() {

    if (sxFacePromise) {

        return sxFacePromise;

    }

    sxFacePromise = (async function () {

        try {

            if (!window.faceapi) {

                await sxLoadScript(SX_FACEAPI_SCRIPT);

            }

            await Promise.all([

                window.faceapi.nets.tinyFaceDetector.loadFromUri(SX_FACEAPI_MODELS),
                window.faceapi.nets.faceLandmark68Net.loadFromUri(SX_FACEAPI_MODELS),
                window.faceapi.nets.faceRecognitionNet.loadFromUri(SX_FACEAPI_MODELS)

            ]);

        } catch (error) {

            sxFacePromise = null;

            console.warn("SCANX face library:", error);

            throw new Error("Face recognition could not load. Check your internet connection and try again.");

        }

    })();

    return sxFacePromise;

}


async function sxGetFaceDescriptor(video) {

    const result = await window.faceapi
        .detectSingleFace(video, new window.faceapi.TinyFaceDetectorOptions({ inputSize: 320, scoreThreshold: 0.5 }))
        .withFaceLandmarks()
        .withFaceDescriptor();

    return result ? Array.from(result.descriptor) : null;

}


async function sxCaptureFaceTemplate(video) {

    const samples = [];

    let attempts = 0;

    while (samples.length < 3 && attempts < 14) {

        attempts++;

        const descriptor = await sxGetFaceDescriptor(video);

        if (descriptor) {

            samples.push(descriptor);

        }

        await sxSleep(350);

    }

    if (samples.length < 3) {

        return null;

    }

    return samples[0].map(function (_, i) {

        const mean = (samples[0][i] + samples[1][i] + samples[2][i]) / 3;

        return Math.round(mean * 10000) / 10000;

    });

}


function sxStartFaceLoop(video, onDescriptor) {

    let running = true;

    let busy = false;

    let timer = null;

    async function tick() {

        if (!running) {

            return;

        }

        if (!busy && video.readyState >= 2) {

            busy = true;

            try {

                const descriptor = await sxGetFaceDescriptor(video);

                if (descriptor && running) {

                    await onDescriptor(descriptor);

                }

            } catch (error) {

                console.warn("SCANX face scan:", error);

            } finally {

                busy = false;

            }

        }

        if (running) {

            timer = setTimeout(tick, 600);

        }

    }

    tick();

    return { stop: function () { running = false; clearTimeout(timer); } };

}


/* =====================================================
   FACE ENROLMENT (staff for a student, or student for self)
   ===================================================== */

function sxEnrollFace(studentId, studentName, afterSave, saveFn) {

    let camera = null;

    let closed = false;

    const video = SxModal.open({

        label: "Face enrolment",

        title: "Enroll face: " + studentName,

        status: "Starting camera…",

        actions: [{ text: "Cancel", ghost: true, onClick: SxModal.close }],

        onClose: function () { closed = true; if (camera) { camera.stop(); } }

    });

    if (!video) {

        sxToast("The camera window is missing from the page.", "bad");

        return;

    }

    video.classList.add("sx-mirror");

    camera = sxCreateCamera(video);

    function captureButtons() {

        SxModal.setActions([
            { text: "Capture face", onClick: capture },
            { text: "Cancel", ghost: true, onClick: SxModal.close }
        ]);

    }

    async function capture() {

        SxModal.setActions([{ text: "Capturing…", disabled: true, onClick: function () { } }]);

        SxModal.setStatus("Hold still and look at the camera…", "info");

        let template = null;

        try {

            template = await sxCaptureFaceTemplate(video);

        } catch (error) {

            template = null;

        }

        if (closed) {

            return;

        }

        if (!template) {

            SxModal.setStatus("No clear face found. Move closer, face the camera and add more light.", "bad");

            captureButtons();

            return;

        }

        try {

            await saveFn(template);

            SxModal.close();

            sxToast("Face enrolled for " + studentName + ".", "ok");

            if (afterSave) {

                afterSave();

            }

        } catch (error) {

            SxModal.setStatus(error.message, "bad");

            captureButtons();

        }

    }

    (async function () {

        try {

            await camera.start("user");

            if (closed) { camera.stop(); return; }

            SxModal.setStatus("Loading face recognition…", "info");

            await sxEnsureFaceApi();

            if (closed) { return; }

            SxModal.setStatus("Look straight at the camera in good light, then press Capture face.", "info");

            captureButtons();

        } catch (error) {

            if (!closed) {

                SxModal.setStatus(sxCameraErrorMessage(error), "bad");

            }

        }

    })();

}


/* =====================================================
   STUDENT SELF-SCAN (check in / out with own face)
   ===================================================== */

function sxVerifyOwnFace(studentName, onCapture, onCancel) {

    let camera = null;

    let loop = null;

    let closed = false;

    let busy = false;

    const video = SxModal.open({

        label: "Face check",

        title: "Scan your face",

        status: "Starting camera…",

        actions: [{ text: "Cancel", ghost: true, onClick: SxModal.close }],

        onClose: function () {

            closed = true;

            if (loop) { loop.stop(); }

            if (camera) { camera.stop(); }

            if (onCancel) { onCancel(); }

        }

    });

    if (!video) {

        sxToast("The camera window is missing from the page.", "bad");

        return;

    }

    video.classList.add("sx-mirror");

    camera = sxCreateCamera(video);

    (async function () {

        try {

            await camera.start("user");

            if (closed) { camera.stop(); return; }

            SxModal.setStatus("Loading face recognition…", "info");

            await sxEnsureFaceApi();

            if (closed) { return; }

            SxModal.setStatus("Look at the camera.", "info");

            loop = sxStartFaceLoop(video, async function (descriptor) {

                if (busy || closed) {

                    return;

                }

                busy = true;

                const result = await onCapture(descriptor);

                busy = false;

                if (result === "ok") {

                    if (loop) { loop.stop(); }

                    closed = true;

                    SxModal.close();

                } else if (result) {

                    SxModal.setStatus(result, "bad");

                }

            });

        } catch (error) {

            if (!closed) {

                SxModal.setStatus(sxCameraErrorMessage(error), "bad");

            }

        }

    })();

}


/* =====================================================
   QR IMAGE
   ===================================================== */

async function sxRenderQr(container, text) {

    sxClear(container);

    try {

        if (!window.qrcode) {

            await sxLoadScript(SX_QRCODE_SCRIPT);

        }

        const qr = window.qrcode(0, "M");

        qr.addData(text);

        qr.make();

        container.appendChild(sxEl("img", {
            class: "sx-qr-img", src: qr.createDataURL(8, 4), alt: "QR code: " + text, width: "200", height: "200"
        }));

    } catch (error) {

        container.appendChild(sxEl("p", {
            class: "sx-muted",
            text: "The QR image could not be drawn (no internet). Your ID code is " + text + "."
        }));

    }

}


/* =====================================================
   APP STATE
   ===================================================== */

const State = {

    role: null,          // "ADMIN" | "TEACHER" | "STUDENT"
    studentId: null,
    displayName: "",
    snapshot: null,       // courses, semesters, subjects, settings, timings, calendar, events, me
    tickHandle: null

};


function sxCoursesById() {

    const map = {};

    (State.snapshot.courses || []).forEach(function (c) { map[c.id] = c; });

    return map;

}

function sxSemestersForCourse(courseId) {

    return (State.snapshot.semesters || [])
        .filter(function (s) { return String(s.courseId) === String(courseId); })
        .sort(function (a, b) { return a.yearNo - b.yearNo || a.name.localeCompare(b.name); });

}

function sxSubjectsForSemester(semesterId) {

    return (State.snapshot.subjects || [])
        .filter(function (s) { return String(s.semesterId) === String(semesterId); })
        .sort(function (a, b) { return a.name.localeCompare(b.name); });

}

function sxSemesterById(id) {

    return (State.snapshot.semesters || []).find(function (s) { return String(s.id) === String(id); });

}

function sxCourseById(id) {

    return (State.snapshot.courses || []).find(function (c) { return String(c.id) === String(id); });

}

function sxSubjectById(id) {

    return (State.snapshot.subjects || []).find(function (s) { return String(s.id) === String(id); });

}


function sxFillCourseSelect(select, placeholder) {

    sxFillSelect(select, placeholder, (State.snapshot.courses || []).map(function (c) {

        return { value: c.id, text: c.name };

    }));

}

function sxFillSemesterSelect(select, courseId, placeholder) {

    sxFillSelect(select, placeholder, sxSemestersForCourse(courseId).map(function (s) {

        return { value: s.id, text: s.name };

    }));

}

function sxFillSubjectSelect(select, semesterId, placeholder) {

    sxFillSelect(select, placeholder, sxSubjectsForSemester(semesterId).map(function (s) {

        return { value: s.id, text: s.name };

    }));

}

function sxLinkCourseSemester(courseSelect, semesterSelect, subjectSelect) {

    courseSelect.addEventListener("change", function () {

        sxFillSemesterSelect(semesterSelect, this.value, "Select semester");

        semesterSelect.disabled = !this.value;

        if (subjectSelect) {

            sxFillSelect(subjectSelect, "Select subject", []);

            subjectSelect.disabled = true;

        }

    });

    if (subjectSelect) {

        semesterSelect.addEventListener("change", function () {

            sxFillSubjectSelect(subjectSelect, this.value, "Select subject");

            subjectSelect.disabled = !this.value;

        });

    }

}


/* =====================================================
   LOGIN / LOGOUT
   ===================================================== */

function sxTopSections() {

    return document.querySelectorAll("body > nav, body > section:not(#student-app):not(#staff-app)");

}

function sxHideLandingAndLogin() {

    document.querySelectorAll("body > nav, #home, #how-it-works, #features, #login-section")
        .forEach(function (el) { el.style.display = "none"; });

}

function openLogin() {

    document.querySelectorAll("body > nav, #home, #how-it-works, #features")
        .forEach(function (el) { el.style.display = "none"; });

    const loginSection = $("login-section");

    loginSection.style.display = "flex";

    loginSection.scrollIntoView({ behavior: "smooth" });

    $("loginUsername").focus();

}

async function sxLoadApp() {

    State.snapshot = await api("snapshot");

    State.role = State.snapshot.role;

    State.studentId = State.snapshot.studentId;

    State.displayName = State.snapshot.displayName;

    sxHideLandingAndLogin();

    if (State.role === "STUDENT") {

        $("student-app").style.display = "block";

        openStudentPage("dashboard-page");

        await refreshStudentViews();

    } else {

        $("staff-app").style.display = "block";

        sxSetupStaffOnlyUi();

        openAdminPage("admin-home");

        await refreshStaffViews();

    }

    if (State.tickHandle) {

        clearInterval(State.tickHandle);

    }

    State.tickHandle = setInterval(sxTick, 60000);

}

/* Refresh the data on the page you are on, without jumping to another page */

async function sxReloadSnapshot() {

    State.snapshot = await api("snapshot");

    State.displayName = State.snapshot.displayName;

    if (State.role === "STUDENT") {

        await refreshStudentViews();

    } else {

        await refreshStaffViews();

    }

}

function sxSetupStaffOnlyUi() {

    const isAdmin = State.role === "ADMIN";

    $("staffRoleLabel").textContent = isAdmin ? "Admin" : "Teacher";

    $("adminOnlySettings").style.display = isAdmin ? "block" : "none";

    $("addStudentPanel").style.display = isAdmin ? "block" : "none";

}

async function sxTick() {

    try {

        if (State.role === "STUDENT") {

            await sxRefreshStudentBell();

        } else if (State.snapshot) {

            await sxRefreshStaffBell();

        }

    } catch (error) {

        // a background refresh failing is not worth bothering the user about

    }

}

const loginForm = $("loginForm");

loginForm.addEventListener("submit", async function (event) {

    event.preventDefault();

    const username = $("loginUsername").value.trim();

    const password = $("loginPassword").value;

    const remember = $("rememberMe").checked;

    const message = $("loginMessage");

    message.textContent = "Signing in…";

    try {

        const result = await api("auth/login", { method: "POST", body: { username: username, password: password } });

        sxSaveLogin(result.token, result.role, result.studentId, remember);

        message.textContent = "Signed in. Opening your dashboard…";

        await sxLoadApp();

    } catch (error) {

        message.textContent = error.message || "Invalid login details. Please try again.";

    }

});

$("forgotPassword").addEventListener("click", function () {

    sxToast("Ask your teacher or admin to reset your password from the Students page.", "info");

});

function sxLogout() {

    const token = sxGetToken();

    sxClearLogin();

    if (State.tickHandle) {

        clearInterval(State.tickHandle);

    }

    if (token) {

        fetch(API_BASE + "auth/logout", {
            method: "POST", headers: { Authorization: "Bearer " + token, "Content-Type": "application/json" }
        }).catch(function () { });

    }

    location.reload();

}

["studentLogout", "staffLogout"].forEach(function (id) {

    const button = $(id);

    if (button) {

        button.addEventListener("click", sxLogout);

    }

});


async function sxChangePassword(oldId, newId, msgId) {

    const oldPassword = $(oldId).value;

    const newPassword = $(newId).value;

    const msg = $(msgId);

    if (!oldPassword || !newPassword) {

        msg.textContent = "Enter your current and new password.";

        return;

    }

    try {

        await api("auth/change-password", { method: "POST", body: { oldPassword: oldPassword, newPassword: newPassword } });

        $(oldId).value = "";

        $(newId).value = "";

        msg.textContent = "Password updated.";

        sxToast("Your password has been changed.", "ok");

    } catch (error) {

        msg.textContent = error.message;

    }

}

$("changePasswordBtn").addEventListener("click", function () {

    sxChangePassword("oldPassword", "newPassword", "changePasswordMsg");

});

$("staffChangePasswordBtn").addEventListener("click", function () {

    sxChangePassword("staffOldPassword", "staffNewPassword", "staffChangePasswordMsg");

});


/* Restore a signed-in session on refresh */

(async function sxRestoreLogin() {

    if (!sxGetToken()) {

        return;

    }

    try {

        await sxLoadApp();

    } catch (error) {

        sxClearLogin();

    }

})();


/* =====================================================
   STUDENT APP
   ===================================================== */

let sxStudentAttendance = null;   // cached GET /student/attendance
let sxStudentReminders = null;
let sxStudentPresence = null;
let sxQrShownFor = null;
const sxCalendar = { year: new Date().getFullYear(), month: new Date().getMonth() };
const sxAttCalendar = { year: new Date().getFullYear(), month: new Date().getMonth() };
let studentSelfScanBusy = false;


const dashboardNavItems = document.querySelectorAll("#student-app .dashboard-nav-item[data-page], #student-app .dashboard-sidebar-bottom [data-page]");
const dashboardPages = document.querySelectorAll("#student-app .dashboard-page");

function openStudentPage(pageId) {

    dashboardNavItems.forEach(function (nav) { nav.classList.toggle("active", nav.dataset.page === pageId); });

    dashboardPages.forEach(function (page) { page.classList.toggle("active-page", page.id === pageId); });

    window.scrollTo({ top: 0 });

    if (pageId === "myid-page") {

        renderStudentMyId();

    }

    if (pageId === "attendance-page" && sxStudentAttendance) {

        renderStudentAttendanceCalendar();

    }

}

dashboardNavItems.forEach(function (item) {

    item.addEventListener("click", function () { openStudentPage(this.dataset.page); });

});

document.querySelectorAll("#student-app [data-goto]").forEach(function (button) {

    button.addEventListener("click", function () { openStudentPage(this.dataset.goto); });

});


function sxMe() {

    return State.snapshot && State.snapshot.me;

}


async function refreshStudentViews() {

    const me = sxMe();

    if (!me) {

        return;

    }

    const hour = new Date().getHours();

    const greeting = hour < 12 ? "Good morning" : hour < 17 ? "Good afternoon" : "Good evening";

    sxSetText("studentGreeting", greeting + ", " + me.name.split(" ")[0] + " 👋");

    sxClear($("studentDashLabel"));

    $("studentDashLabel").appendChild(sxEl("span", {}, [

        "Student dashboard · ",
        sxEl("span", { class: "keep-case", text: me.courseName + " · " + me.semesterName })

    ]));

    sxSetText("studentAvatar", me.name.charAt(0).toUpperCase());

    const [attendance, reminders, presence] = await Promise.all([

        api("student/attendance"),
        api("student/reminders"),
        api("student/presence")

    ]);

    sxStudentAttendance = attendance;

    sxStudentReminders = reminders.reminders;

    sxStudentPresence = presence;

    renderStudentHome();

    renderStudentAttendancePage();

    renderStudentCalendar();

    renderStudentReminders();

    renderStudentMyId();

    await sxRefreshStudentBell();

}


function renderStudentHome() {

    const me = sxMe();

    const att = sxStudentAttendance;

    const target = State.snapshot.settings.targetPercent;

    sxSetText("overallAttendance", sxPercentText(att.percent));

    const bar = $("attendanceProgress");

    if (bar) {

        bar.style.width = (att.percent || 0) + "%";

    }

    sxSetText("fullDaysCount", String(att.fullDays));

    sxSetText("halfDaysCount", String(att.halfDays));

    sxSetText("absentDaysCount", String(att.absentDays));

    const onTrack = att.percent !== null && att.percent >= target - 1e-9;

    const statusPill = $("attendanceStatus");

    statusPill.textContent = att.percent === null ? "No data yet" : onTrack ? "On track" : "Needs attention";

    statusPill.className = "attendance-status" + (att.percent === null ? "" : onTrack ? " is-good" : " is-warn");

    sxSetText("attendanceDescription",

        att.percent === null

            ? "Your attendance will show here after your first gate scan."

            : onTrack

                ? "You are above the required " + target + "% target."

                : "You need more full days to reach the required " + target + "% target."

    );

    const today = att.days.length ? att.days[att.days.length - 1] : null;

    const todayKey = sxDateKey();

    const todayEntry = todayKey === (today ? today.date : null) ? today : null;

    const heading = $("todayStatusHeading");

    const text = $("todayStatusText");

    if (!todayEntry) {

        heading.textContent = "Not a working day, or not scanned yet";

        text.textContent = "Scan in at the gate, or use the button to check yourself in with your face.";

    } else if (todayEntry.status === "FULL") {

        heading.textContent = "Marked as a full day";

        text.textContent = "You're all set for today.";

    } else if (todayEntry.status === "HALF") {

        heading.textContent = "Marked as a half day";

        text.textContent = "You scanned out before the half-day time today.";

    } else {

        heading.textContent = "Not scanned in yet";

        text.textContent = "Scan in at the gate, or check yourself in with your face below.";

    }

    const list = $("homeEventsList");

    sxClear(list);

    const todayIso = sxDateKey();

    const upcoming = (State.snapshot.events || []).filter(function (e) { return e.date >= todayIso; }).slice(0, 3);

    if (!upcoming.length) {

        list.appendChild(sxEl("p", { class: "sx-empty", text: "No upcoming events." }));

    }

    upcoming.forEach(function (event) {

        const date = sxParseDateKey(event.date);

        list.appendChild(sxEl("div", { class: "event-item" }, [

            sxEl("div", { class: "event-date" }, [
                sxEl("strong", { text: String(date.getDate()) }),
                sxEl("span", { text: SX_MONTHS_SHORT[date.getMonth()] })
            ]),

            sxEl("div", { class: "event-info" }, [
                sxEl("h3", { text: event.title }),
                sxEl("p", { text: [sxFormatClock(event.time), event.location].filter(Boolean).join(" · ") || event.type })
            ]),

            sxEl("span", { class: "event-tag " + sxTagClass(event.type), text: event.type.toUpperCase() })

        ]));

    });

    sxSetText("homePlannerPercent", sxPercentText(att.percent));

    sxSetText("homePlannerMessage",

        att.percent === null

            ? "Set a target date in the planner to see your plan."

            : onTrack

                ? "You're on track. Open the planner to see how many days you can miss."

                : "Open the planner to see how many more full days you need."

    );

}


function sxTagClass(type) {

    if (type === "Test" || type === "Exam") return "test-tag";

    if (type === "Assignment") return "assignment-tag";

    return "workshop-tag";

}


function renderStudentAttendancePage() {

    const att = sxStudentAttendance;

    sxSetText("attStatOverall", sxPercentText(att.percent));

    sxSetText("attStatFull", String(att.fullDays));

    sxSetText("attStatHalf", String(att.halfDays));

    sxSetText("attStatAbsent", String(att.absentDays));

    const subjectRows = $("subjectRows");

    sxClear(subjectRows);

    if (!att.subjects.length) {

        subjectRows.appendChild(sxEl("p", { class: "sx-empty", text: "No subjects in your semester yet." }));

    }

    const target = State.snapshot.settings.targetPercent;

    att.subjects.forEach(function (s) {

        const low = s.percent !== null && s.percent < target - 1e-9;

        subjectRows.appendChild(sxEl("div", { class: "attendance-row" + (low ? " sx-low" : "") }, [

            sxEl("strong", { text: s.subjectName }),
            sxEl("span", { text: String(s.attended) }),
            sxEl("span", { text: String(s.total) }),
            sxEl("strong", { text: sxPercentText(s.percent) })

        ]));

    });

    const presenceRows = $("presenceRows");

    sxClear(presenceRows);

    const visits = sxStudentPresence.visits;

    if (!visits.length) {

        presenceRows.appendChild(sxEl("p", { class: "sx-empty", text: "Your gate scans will appear here." }));

    }

    visits.slice(0, 14).forEach(function (v) {

        const ms = v.exit ? v.durationMs : v.incomplete ? null : Date.now() - new Date(v.entry).getTime();

        presenceRows.appendChild(sxEl("div", { class: "sx-table-row" }, [

            sxEl("strong", { text: sxRelativeDay(v.date) }),
            sxEl("span", { text: sxFormatTime(v.entry) }),
            sxEl("span", { text: v.exit ? sxFormatTime(v.exit) : v.incomplete ? "Not scanned" : "Inside" }),
            sxEl("b", { text: ms === null ? "—" : sxFormatDuration(ms) })

        ]));

    });

    renderStudentAttendanceCalendar();

}


function renderStudentAttendanceCalendar() {

    const holder = $("attCalDays");

    if (!holder) {

        return;

    }

    const year = sxAttCalendar.year;

    const month = sxAttCalendar.month;

    sxSetText("attCalTitle", SX_MONTHS_LONG[month] + " " + year);

    const byDate = {};

    (sxStudentAttendance.days || []).forEach(function (d) { byDate[d.date] = d; });

    sxClear(holder);

    const offset = (new Date(year, month, 1).getDay() + 6) % 7;

    for (let i = 0; i < offset; i++) {

        holder.appendChild(sxEl("span", { class: "sx-cal-empty", "aria-hidden": "true" }));

    }

    const daysInMonth = new Date(year, month + 1, 0).getDate();

    const todayKey = sxDateKey();

    for (let day = 1; day <= daysInMonth; day++) {

        const key = year + "-" + sxPad(month + 1) + "-" + sxPad(day);

        const entry = byDate[key];

        const classes = [];

        if (key === todayKey) classes.push("calendar-today");

        if (entry) classes.push("sx-day-" + entry.status.toLowerCase());

        holder.appendChild(sxEl("span", {
            class: classes.join(" "),
            title: entry ? entry.status : "",
            text: String(day)
        }));

    }

}

const attCalPrev = $("attCalPrev");
const attCalNext = $("attCalNext");

if (attCalPrev) {

    attCalPrev.addEventListener("click", function () {

        const d = new Date(sxAttCalendar.year, sxAttCalendar.month - 1, 1);

        sxAttCalendar.year = d.getFullYear(); sxAttCalendar.month = d.getMonth();

        renderStudentAttendanceCalendar();

    });

}

if (attCalNext) {

    attCalNext.addEventListener("click", function () {

        const d = new Date(sxAttCalendar.year, sxAttCalendar.month + 1, 1);

        sxAttCalendar.year = d.getFullYear(); sxAttCalendar.month = d.getMonth();

        renderStudentAttendanceCalendar();

    });

}


/* ---------- planner ---------- */

const targetDateInput = $("targetDate");

if (targetDateInput) {

    targetDateInput.min = sxDateKey(sxAddDays(new Date(), 1));

}

async function studentCalculatePlan() {

    const target = Number($("targetAttendance").value);

    const dateKey = $("targetDate").value;

    const box = $("plannerResultText");

    if (!(target >= 1 && target <= 100)) {

        sxClear(box);
        box.appendChild(sxEl("div", { class: "sx-plan sx-plan-bad" }, [sxEl("strong", { class: "sx-plan-headline", text: "Target must be between 1% and 100%." })]));

        return;

    }

    if (!dateKey) {

        sxClear(box);
        box.appendChild(sxEl("div", { class: "sx-plan sx-plan-bad" }, [sxEl("strong", { class: "sx-plan-headline", text: "Pick the date you want to reach your target by." })]));

        return;

    }

    let plan;

    try {

        plan = await api("student/plan?target=" + target + "&by=" + dateKey);

    } catch (error) {

        sxToast(error.message, "bad");

        return;

    }

    const pct = function (v) { return v === null ? "—" : (Math.round(v * 10) / 10) + "%"; };

    const dateText = sxFormatDateLong(dateKey);

    let type, headline, detail;

    if (plan.futureWorkingDays === 0) {

        type = (plan.currentPercent || 0) >= target - 1e-9 ? "ok" : "warn";

        headline = "No working days before " + dateText + ".";

        detail = "That date is too close, so your attendance would stay at " + pct(plan.currentPercent) + ".";

    } else if (plan.need <= 0) {

        type = "ok";

        headline = "You're already on track for " + target + "%.";

        detail = plan.canMiss >= plan.futureWorkingDays

            ? "Even if you missed all " + plan.futureWorkingDays + " remaining working days, you'd still finish at " + pct(plan.ifNonePercent) + "."

            : "You can miss up to " + plan.canMiss + " of the next " + plan.futureWorkingDays + " working days and stay at or above " + target + "%.";

    } else if (plan.reachable) {

        type = "warn";

        headline = "Attend at least " + plan.need + " of the next " + plan.futureWorkingDays + " working days.";

        detail = "That gets you to " + pct(plan.ifAllPercent - (plan.need < plan.futureWorkingDays ? 0 : 0)) + " if you attend exactly that many, by " + dateText + ".";

    } else {

        type = "bad";

        headline = target + "% is out of reach by " + dateText + ".";

        detail = "You'd need " + plan.need + " full days but only " + plan.futureWorkingDays

            + " working days remain. Attending every one gets you to " + pct(plan.ifAllPercent) + ".";

    }

    sxClear(box);

    box.appendChild(sxEl("div", { class: "sx-plan sx-plan-" + type }, [

        sxEl("strong", { class: "sx-plan-headline", text: headline }),
        sxEl("p", { class: "sx-plan-detail", text: detail }),

        sxEl("div", { class: "sx-plan-grid" }, [

            sxEl("div", {}, [sxEl("span", { text: "Now" }), sxEl("strong", { text: pct(plan.currentPercent) })]),
            sxEl("div", {}, [sxEl("span", { text: "Working days until " + dateText }), sxEl("strong", { text: String(plan.futureWorkingDays) })]),
            sxEl("div", {}, [sxEl("span", { text: "If you attend all" }), sxEl("strong", { text: pct(plan.ifAllPercent) })]),
            sxEl("div", {}, [sxEl("span", { text: "If you attend none" }), sxEl("strong", { text: pct(plan.ifNonePercent) })])

        ])

    ]));

}

$("calculatePlanner").addEventListener("click", studentCalculatePlan);


/* ---------- calendar (events + reminders) ---------- */

function renderStudentCalendar() {

    const holder = $("calDays");

    if (!holder) {

        return;

    }

    const year = sxCalendar.year;

    const month = sxCalendar.month;

    sxSetText("calTitle", SX_MONTHS_LONG[month] + " " + year);

    const me = sxMe();

    const officialByDay = {};

    (State.snapshot.events || []).forEach(function (e) { (officialByDay[e.date] = officialByDay[e.date] || []).push(e); });

    const holidayByDay = {};

    (State.snapshot.calendarDays || []).forEach(function (d) {

        if (d.kind === "HOLIDAY" && (!d.courseId || String(d.courseId) === String(me.courseId)) && (!d.yearNo || d.yearNo === me.yearNo)) {

            holidayByDay[d.date] = d;

        }

    });

    const personalByDay = {};

    sxStudentReminders.forEach(function (r) { if (r.date) { (personalByDay[r.date] = personalByDay[r.date] || []).push(r); } });

    sxClear(holder);

    const offset = (new Date(year, month, 1).getDay() + 6) % 7;

    for (let i = 0; i < offset; i++) {

        holder.appendChild(sxEl("span", { class: "sx-cal-empty", "aria-hidden": "true" }));

    }

    const daysInMonth = new Date(year, month + 1, 0).getDate();

    const todayKey = sxDateKey();

    for (let day = 1; day <= daysInMonth; day++) {

        const key = year + "-" + sxPad(month + 1) + "-" + sxPad(day);

        const official = officialByDay[key] || [];

        const personal = personalByDay[key] || [];

        const holiday = holidayByDay[key];

        const classes = [];

        if (key === todayKey) classes.push("calendar-today");

        if (holiday) classes.push("sx-cal-holiday");

        else if (official.length) classes.push("calendar-event");

        if (personal.length) classes.push("sx-cal-reminder");

        const titles = official.map(function (e) { return e.title; })
            .concat(personal.map(function (r) { return "Reminder: " + r.text; }))
            .concat(holiday ? ["Holiday: " + (holiday.note || "")] : []);

        holder.appendChild(sxEl("span", { class: classes.join(" "), title: titles.join("\n"), text: String(day) }));

    }

    const list = $("calEventList");

    sxClear(list);

    const prefix = year + "-" + sxPad(month + 1) + "-";

    const items = [];

    Object.keys(officialByDay).forEach(function (key) { if (key.indexOf(prefix) === 0) { officialByDay[key].forEach(function (e) { items.push({ key: key, kind: "event", event: e }); }); } });

    Object.keys(personalByDay).forEach(function (key) { if (key.indexOf(prefix) === 0) { personalByDay[key].forEach(function (r) { items.push({ key: key, kind: "reminder", reminder: r }); }); } });

    Object.keys(holidayByDay).forEach(function (key) { if (key.indexOf(prefix) === 0) { items.push({ key: key, kind: "holiday", holiday: holidayByDay[key] }); } });

    items.sort(function (a, b) { return a.key.localeCompare(b.key); });

    if (!items.length) {

        list.appendChild(sxEl("p", { class: "sx-empty", text: "Nothing planned this month." }));

        return;

    }

    items.forEach(function (item) {

        const date = sxParseDateKey(item.key);

        const title = item.kind === "event" ? item.event.title : item.kind === "reminder" ? item.reminder.text : "Holiday";

        const sub = item.kind === "event"

            ? "Official " + item.event.type.toLowerCase() + (item.event.location ? " · " + item.event.location : "")

            : item.kind === "reminder" ? "Your reminder" : (item.holiday.note || "No classes today");

        list.appendChild(sxEl("div", { class: "calendar-event-item" }, [

            sxEl("strong", { text: date.getDate() + " " + SX_MONTHS_SHORT[date.getMonth()] }),
            sxEl("div", {}, [sxEl("h3", { text: title }), sxEl("p", { text: sub })])

        ]));

    });

}

$("calPrev").addEventListener("click", function () {

    const d = new Date(sxCalendar.year, sxCalendar.month - 1, 1);

    sxCalendar.year = d.getFullYear(); sxCalendar.month = d.getMonth();

    renderStudentCalendar();

});

$("calNext").addEventListener("click", function () {

    const d = new Date(sxCalendar.year, sxCalendar.month + 1, 1);

    sxCalendar.year = d.getFullYear(); sxCalendar.month = d.getMonth();

    renderStudentCalendar();

});


/* ---------- reminders ---------- */

function renderStudentReminders() {

    const todayKey = sxDateKey();

    const official = $("officialReminderList");

    sxClear(official);

    const upcoming = (State.snapshot.events || []).filter(function (e) { return e.date >= todayKey; });

    if (!upcoming.length) {

        official.appendChild(sxEl("p", { class: "sx-empty", text: "Nothing from your teachers right now." }));

    }

    upcoming.forEach(function (event) {

        official.appendChild(sxEl("div", { class: "reminder-item sx-official" }, [

            sxEl("span", { text: "◆", "aria-hidden": "true" }),

            sxEl("div", {}, [
                sxEl("strong", { text: event.title }),
                sxEl("p", { text: [event.type, sxRelativeDay(event.date), sxFormatClock(event.time), event.location].filter(Boolean).join(" · ") })
            ]),

            sxEl("span", { class: "sx-badge sx-badge-lock", text: "Set by staff" })

        ]));

    });

    const list = $("reminderList");

    sxClear(list);

    if (!sxStudentReminders.length) {

        list.appendChild(sxEl("p", { class: "sx-empty", text: "No reminders yet. Add one above." }));

    }

    sxStudentReminders.slice().sort(function (a, b) {

        if (a.done !== b.done) return a.done ? 1 : -1;

        return (a.date || "9999").localeCompare(b.date || "9999");

    }).forEach(function (reminder) {

        const overdue = !reminder.done && reminder.date && reminder.date < todayKey;

        const toggle = async function () {

            try {

                await api("student/reminders/" + reminder.id + "/toggle", { method: "POST" });

                await refreshStudentViews();

            } catch (error) {

                sxToast(error.message, "bad");

            }

        };

        list.appendChild(sxEl("div", { class: "reminder-item" + (reminder.done ? " sx-done" : "") + (overdue ? " sx-overdue" : "") }, [

            sxEl("span", {
                class: "sx-check", role: "button", tabindex: "0",
                "aria-label": reminder.done ? "Mark as not done" : "Mark as done",
                text: reminder.done ? "●" : "○", onclick: toggle,
                onkeydown: function (e) { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); toggle(); } }
            }),

            sxEl("div", {}, [
                sxEl("strong", { text: reminder.text }),
                sxEl("p", { text: reminder.date ? (overdue ? "Overdue · " : "") + sxRelativeDay(reminder.date) : "No date" })
            ]),

            sxEl("button", {
                type: "button", class: "sx-icon-btn", "aria-label": "Delete reminder", text: "✕",
                onclick: async function () {

                    try {

                        await api("student/reminders/" + reminder.id, { method: "DELETE" });

                        await refreshStudentViews();

                    } catch (error) {

                        sxToast(error.message, "bad");

                    }

                }
            })

        ]));

    });

}

const reminderInput = $("reminderInput");
const reminderDate = $("reminderDate");

$("addReminder").addEventListener("click", submitReminder);

reminderInput.addEventListener("keydown", function (event) { if (event.key === "Enter") { event.preventDefault(); submitReminder(); } });

async function submitReminder() {

    const text = reminderInput.value.trim();

    if (!text) {

        sxToast("Enter a reminder.", "warn");

        reminderInput.focus();

        return;

    }

    try {

        await api("student/reminders", { method: "POST", body: { text: text, date: reminderDate.value } });

        reminderInput.value = "";

        reminderDate.value = "";

        await refreshStudentViews();

    } catch (error) {

        sxToast(error.message, "bad");

    }

}


/* ---------- self-scan (gate check-in with face) ---------- */

function studentSelfScan() {

    if (studentSelfScanBusy) {

        return;

    }

    const me = sxMe();

    if (!me.hasFace) {

        sxToast("Enroll your face first, on the My ID page.", "warn");

        openStudentPage("myid-page");

        return;

    }

    studentSelfScanBusy = true;

    sxVerifyOwnFace(

        me.name,

        async function (descriptor) {

            try {

                const result = await api("student/self-scan", { method: "POST", body: { descriptor: descriptor } });

                if (result.ok) {

                    sxToast(result.message, "ok");

                    await refreshStudentViews();

                    return "ok";

                }

                if (result.code === "FACE_MISMATCH") {

                    return result.message;

                }

                sxToast(result.message, "warn");

                return "ok"; // stop the loop; message already shown as a toast

            } catch (error) {

                sxToast(error.message, "bad");

                return "ok";

            }

        },

        function () { studentSelfScanBusy = false; }

    );

}

$("studentSelfScan").addEventListener("click", studentSelfScan);


/* ---------- My ID ---------- */

function renderStudentMyId() {

    const me = sxMe();

    sxSetText("myIdNumber", me.id);

    sxSetText("myIdName", me.name);

    sxSetText("myIdClass", me.courseName + " · " + me.semesterName);

    const qrHolder = $("myIdQr");

    if (qrHolder && sxQrShownFor !== me.id) {

        sxQrShownFor = me.id;

        sxRenderQr(qrHolder, "SCANX:" + me.id);

    }

    const status = $("faceStatus");

    const button = $("enrollMyFace");

    if (me.hasFace) {

        status.textContent = "Face enrolled" + (me.faceEnrolledAt ? " on " + sxFormatDateLong(sxDateKey(new Date(me.faceEnrolledAt))) : "")

            + ". If it stops matching, ask a teacher to enroll it again.";

        button.disabled = true;

        button.textContent = "Face enrolled";

    } else {

        status.textContent = "No face enrolled yet. Enroll once to check yourself in with your face.";

        button.disabled = false;

        button.textContent = "Enroll my face";

    }

}

$("enrollMyFace").addEventListener("click", function () {

    const me = sxMe();

    if (me.hasFace) {

        return;

    }

    sxEnrollFace(me.id, me.name, async function () { await sxReloadSnapshot(); }, async function (descriptor) {

        await api("student/face", { method: "PUT", body: { descriptor: descriptor } });

    });

});


/* ---------- bell ---------- */

async function sxRefreshStudentBell() {

    const panel = $("studentBellPanel");

    const dot = $("studentBellDot");

    if (!State.snapshot || State.role !== "STUDENT") {

        return;

    }

    const me = sxMe();

    const todayKey = sxDateKey();

    const soon = (State.snapshot.events || []).filter(function (e) {

        return e.date >= todayKey && e.date <= sxDateKey(sxAddDays(new Date(), 5));

    });

    const dueReminders = (sxStudentReminders || []).filter(function (r) {

        return !r.done && r.date && r.date <= sxDateKey(sxAddDays(new Date(), 2));

    });

    dot.hidden = soon.length + dueReminders.length === 0;

}

$("studentBell").addEventListener("click", function () {

    const panel = $("studentBellPanel");

    panel.hidden = !panel.hidden;

    if (panel.hidden) {

        return;

    }

    sxClear(panel);

    const todayKey = sxDateKey();

    const soon = (State.snapshot.events || []).filter(function (e) { return e.date >= todayKey; }).slice(0, 4);

    const dueReminders = (sxStudentReminders || []).filter(function (r) { return !r.done && r.date; }).slice(0, 4);

    panel.appendChild(sxEl("p", { class: "sx-panel-title", text: "Coming up" }));

    if (!soon.length && !dueReminders.length) {

        panel.appendChild(sxEl("p", { class: "sx-empty", text: "Nothing due soon." }));

    }

    soon.forEach(function (e) {

        panel.appendChild(sxEl("div", { class: "sx-notify-row" }, [sxEl("strong", { text: e.title }), sxEl("span", { text: sxRelativeDay(e.date) })]));

    });

    dueReminders.forEach(function (r) {

        panel.appendChild(sxEl("div", { class: "sx-notify-row" }, [sxEl("strong", { text: r.text }), sxEl("span", { text: sxRelativeDay(r.date) })]));

    });

    $("studentBellDot").hidden = true;

});


/* =====================================================
   STAFF APP: navigation
   ===================================================== */

const adminNavItems = document.querySelectorAll("#staff-app .admin-nav-item[data-admin-page], #staff-app .quick-action[data-admin-page]");
const adminPages = document.querySelectorAll("#staff-app .admin-page");

async function openAdminPage(pageId) {

    if (pageId !== "admin-scanner") {

        adminStopScan();

    }

    adminPages.forEach(function (page) { page.classList.toggle("active-admin-page", page.id === pageId); });

    document.querySelectorAll("#staff-app .admin-nav-item").forEach(function (item) {

        item.classList.toggle("active", item.dataset.adminPage === pageId);

    });

    window.scrollTo({ top: 0 });

    try {

        if (pageId === "admin-home") { await renderAdminHome(); }
        else if (pageId === "admin-scanner") { showScannerReady(); renderScanBanner(); renderAdminScanHistory(); }
        else if (pageId === "admin-register") { renderRegisterPage(); }
        else if (pageId === "admin-students") { await renderAdminStudents(); }
        else if (pageId === "admin-attendance") { /* on lookup */ }
        else if (pageId === "admin-events") { renderAdminEvents(); }
        else if (pageId === "admin-reports") { await renderAdminReports(); }
        else if (pageId === "admin-settings") { renderAdminSettings(); }

    } catch (error) {

        sxToast(error.message, "bad");

    }

}

document.querySelectorAll("#staff-app [data-admin-page]").forEach(function (item) {

    item.addEventListener("click", function () { openAdminPage(this.dataset.adminPage); });

});


async function refreshStaffViews() {

    sxSetText("staffGreeting", "Good " + (new Date().getHours() < 12 ? "morning" : new Date().getHours() < 17 ? "afternoon" : "evening") + ", " + State.displayName + ".");

    sxSetText("staffWelcomeLabel", (State.role === "ADMIN" ? "Admin" : "Teacher") + " dashboard");

    $("staffWelcomeLabel").classList.add("keep-case");

    sxSetText("staffAvatar", State.displayName.charAt(0).toUpperCase());

    sxSetupRegisterCourseSelects();

    sxSetupStudentCourseSelects();

    sxSetupEventCourseSelect();

    await renderAdminHome();

    await sxRefreshStaffBell();

}


/* =====================================================
   STAFF: GATE SCANNER
   ===================================================== */

const adminScanResult = $("adminScanResult");
const adminQrScan = $("adminQrScan");
const adminIdScan = $("adminIdScan");
const adminFaceScan = $("adminFaceScan");
const adminScanStop = $("adminScanStop");

const adminScanner = { kind: null, camera: null, loop: null, recent: {}, gotResult: false };
let adminScanHistory = [];


function adminScanRecently(key, ms) {

    const now = Date.now();

    if (adminScanner.recent[key] && now - adminScanner.recent[key] < ms) {

        return true;

    }

    adminScanner.recent[key] = now;

    return false;

}

function showAdminResult(type, title, detail) {

    if (!adminScanResult) {

        return;

    }

    adminScanResult.className = "admin-scan-result sx-result sx-result-" + type;

    sxClear(adminScanResult);

    adminScanResult.appendChild(sxEl("strong", { class: "sx-result-title", text: title }));

    if (detail) {

        adminScanResult.appendChild(sxEl("span", { class: "sx-result-detail", text: detail }));

    }

}

async function renderScanBanner() {

    const banner = $("scanSessionBanner");

    if (!banner) {

        return;

    }

    try {

        const data = await api("staff/presence/today");

        const inside = data.visits.filter(function (v) { return !v.exit && !v.incomplete; }).length;

        sxSetText("scanInsideCount", inside + " inside now · " + data.visits.length + " visit" + (data.visits.length === 1 ? "" : "s") + " today");

    } catch (error) {

        sxSetText("scanInsideCount", "Could not load today's visits.");

    }

}

/* The starting message, shown only when the scanner page is opened (never over a real result) */

function showScannerReady() {

    if (!adminScanner.kind) {

        showAdminResult("info", "Ready to scan.", "The first scan of the day is an entry, the next is an exit.");

    }

}

async function renderAdminScanHistory() {

    const list = $("adminScanHistory");

    if (!list) {

        return;

    }

    sxClear(list);

    if (!adminScanHistory.length) {

        list.appendChild(sxEl("p", { class: "sx-empty", text: "Scans will appear here." }));

        return;

    }

    adminScanHistory.slice(-6).reverse().forEach(function (entry) {

        list.appendChild(sxEl("div", { class: "sx-history-row" }, [

            sxEl("strong", { text: entry.studentName }),
            sxEl("span", { text: entry.action === "ENTRY" ? "Entered college" : "Left college" }),
            sxEl("small", { text: sxFormatTime(entry.time) + " · " + entry.method })

        ]));

    });

}


function processStaffResult(result, method) {

    if (!result.ok) {

        showAdminResult(result.code === "TOO_SOON" ? "warn" : "bad",

            result.code === "TOO_SOON" ? "Already scanned" : "Not recorded", result.message);

        return;

    }

    showAdminResult("ok", result.studentName + (result.action === "ENTRY" ? " entered" : " left"),

        (result.action === "ENTRY" ? "Entry" : "Exit") + " at " + sxFormatTime(result.time)

        + (result.durationMs ? " · " + sxFormatDuration(result.durationMs) + " in college this visit." : "."));

    adminScanHistory.push({ studentName: result.studentName, action: result.action, time: result.time, method: method });

    renderAdminScanHistory();

    renderScanBanner();

    if (navigator.vibrate) { navigator.vibrate(60); }

}

async function onAdminCode(text) {

    if (adminScanRecently("code:" + text, 8000)) {

        return;

    }

    adminScanner.gotResult = true;

    try {

        const result = await api("staff/scan", { method: "POST", body: { code: text, method: adminScanner.kind === "id" ? "id-card" : "qr" } });

        processStaffResult(result, adminScanner.kind === "id" ? "id-card" : "qr");

    } catch (error) {

        showAdminResult("bad", "Not recorded", error.message);

    }

}

async function onAdminFace(descriptor) {

    adminScanner.gotResult = true;

    try {

        const result = await api("staff/scan", { method: "POST", body: { descriptor: descriptor } });

        if (result.code === "FACE_NOT_RECOGNISED") {

            if (!adminScanRecently("unknown-face", 3000)) {

                showAdminResult("bad", "Face not recognised", result.message);

            }

            return;

        }

        if (adminScanRecently("face:" + result.studentId, 8000)) {

            return;

        }

        processStaffResult(result, "face");

    } catch (error) {

        showAdminResult("bad", "Not recorded", error.message);

    }

}

function adminMarkScanButtons(kind) {

    [[adminQrScan, "qr"], [adminIdScan, "id"], [adminFaceScan, "face"]].forEach(function (pair) {

        if (pair[0]) { pair[0].classList.toggle("sx-active", pair[1] === kind); }

    });

    if (adminScanStop) { adminScanStop.hidden = !kind; }

}

function adminStopScan() {

    if (adminScanner.loop) { adminScanner.loop.stop(); }

    if (adminScanner.camera) { adminScanner.camera.stop(); }

    adminScanner.loop = null;

    adminScanner.camera = null;

    adminScanner.kind = null;

    const frame = $("adminScanFrame");

    const box = $("adminScanCamera");

    if (frame) frame.hidden = false;

    if (box) box.hidden = true;

    adminMarkScanButtons(null);

}

async function adminStartScan(kind) {

    const video = $("adminScanVideo");

    if (!video) {

        return;

    }

    adminStopScan();

    adminScanner.kind = kind;

    adminScanner.recent = {};

    adminScanner.gotResult = false;

    adminMarkScanButtons(kind);

    $("adminScanFrame").hidden = true;

    $("adminScanCamera").hidden = false;

    video.classList.toggle("sx-mirror", kind === "face");

    showAdminResult("info", "Starting camera…", "");

    const camera = sxCreateCamera(video);

    adminScanner.camera = camera;

    try {

        await camera.start(kind === "face" ? "user" : "environment");

        if (adminScanner.camera !== camera) { camera.stop(); return; }

        if (kind === "face") {

            showAdminResult("info", "Loading face recognition…", "");

            await sxEnsureFaceApi();

            if (adminScanner.camera !== camera) { return; }

            adminScanner.loop = sxStartFaceLoop(video, onAdminFace);

            if (!adminScanner.gotResult) {

                showAdminResult("info", "Looking for a face…", "Ask the student to look at the camera.");

            }

            return;

        }

        const loop = sxStartCodeLoop(video, kind === "qr" ? SX_QR_FORMATS : SX_ID_FORMATS, onAdminCode);

        adminScanner.loop = loop;

        const info = await loop.ready;

        if (adminScanner.camera !== camera || adminScanner.gotResult) {

            return;

        }

        if (kind === "id" && info.fallback) {

            showAdminResult("warn", "Reading QR codes only", "This browser cannot read barcodes. Use Chrome or Edge, a USB barcode scanner, or type the ID below.");

        } else {

            showAdminResult("info", "Ready to scan", kind === "qr" ? "Hold the student's QR code in front of the camera." : "Hold the ID card in front of the camera.");

        }

    } catch (error) {

        if (adminScanner.camera === camera) {

            adminStopScan();

            showAdminResult("bad", "Scanner could not start", sxCameraErrorMessage(error));

        }

    }

}

adminQrScan.addEventListener("click", function () { adminStartScan("qr"); });
adminIdScan.addEventListener("click", function () { adminStartScan("id"); });
adminFaceScan.addEventListener("click", function () { adminStartScan("face"); });
adminScanStop.addEventListener("click", function () { adminStopScan(); renderScanBanner(); });

const adminManualId = $("adminManualId");
const adminManualSubmit = $("adminManualSubmit");

async function adminSubmitManualId() {

    const code = adminManualId.value.trim();

    if (!code) {

        return;

    }

    adminManualId.value = "";

    try {

        const result = await api("staff/scan", { method: "POST", body: { studentId: code, method: "manual" } });

        processStaffResult(result, "manual");

    } catch (error) {

        showAdminResult("bad", "Not recorded", error.message);

    }

    adminManualId.focus();

}

adminManualSubmit.addEventListener("click", adminSubmitManualId);

adminManualId.addEventListener("keydown", function (event) {

    if (event.key === "Enter") { event.preventDefault(); adminSubmitManualId(); }

});


/* =====================================================
   STAFF: SUBJECT REGISTER
   ===================================================== */

let regClassStudents = [];
let regCurrentSubjectId = null;

function sxSetupRegisterCourseSelects() {

    const courseSelect = $("regCourse");

    const semesterSelect = $("regSemester");

    const subjectSelect = $("regSubject");

    sxFillCourseSelect(courseSelect, "Select course");

    sxLinkCourseSemester(courseSelect, semesterSelect, subjectSelect);

    $("regDate").value = sxDateKey();

    $("regDate").max = sxDateKey();

}

async function renderRegisterPage() {

    $("regRows").textContent = "";

    $("regTakenInfo").textContent = "No class loaded";

    $("regSave").disabled = true;

}

$("regLoad").addEventListener("click", async function () {

    const subjectId = $("regSubject").value;

    const semesterId = $("regSemester").value;

    const date = $("regDate").value;

    const period = Number($("regPeriod").value) || 1;

    if (!subjectId || !date) {

        sxToast("Choose a course, semester, subject and date.", "warn");

        return;

    }

    regCurrentSubjectId = subjectId;

    try {

        const [taken, students] = await Promise.all([

            api("staff/register?subjectId=" + subjectId + "&date=" + date + "&period=" + period),
            api("staff/students?semesterId=" + semesterId)

        ]);

        regClassStudents = students.students;

        renderRegisterRows(taken.presentIds);

        $("regTakenInfo").textContent = taken.takenBy

            ? "Last saved by " + taken.takenBy + " · " + regClassStudents.length + " students in this class"

            : regClassStudents.length + " students in this class · not taken yet";

        $("regSave").disabled = false;

    } catch (error) {

        sxToast(error.message, "bad");

    }

});

function renderRegisterRows(presentIds) {

    const holder = $("regRows");

    sxClear(holder);

    if (!regClassStudents.length) {

        holder.appendChild(sxEl("p", { class: "sx-empty", text: "No students in this class yet." }));

        return;

    }

    const present = new Set(presentIds || []);

    regClassStudents.forEach(function (student) {

        const row = sxEl("label", { class: "sx-register-row" }, [

            sxEl("input", { type: "checkbox", "data-student-id": student.id, checked: present.has(student.id) || null }),
            sxEl("span", { class: "sx-register-id", text: student.id }),
            sxEl("span", { class: "sx-register-name", text: student.name })

        ]);

        holder.appendChild(row);

    });

}

$("regMarkAll").addEventListener("click", function () {

    document.querySelectorAll("#regRows input[type=checkbox]").forEach(function (cb) { cb.checked = true; });

});

$("regSave").addEventListener("click", async function () {

    const presentIds = Array.from(document.querySelectorAll("#regRows input[type=checkbox]:checked"))
        .map(function (cb) { return cb.dataset.studentId; });

    try {

        await api("staff/register", { method: "POST", body: {

            subjectId: Number(regCurrentSubjectId),
            date: $("regDate").value,
            period: Number($("regPeriod").value) || 1,
            presentIds: presentIds

        } });

        sxToast("Register saved: " + presentIds.length + " present.", "ok");

        $("regTakenInfo").textContent = "Saved just now · " + regClassStudents.length + " students in this class";

    } catch (error) {

        sxToast(error.message, "bad");

    }

});


/* =====================================================
   STAFF: STUDENTS
   ===================================================== */

let staffStudentsCache = [];

function sxSetupStudentCourseSelects() {

    const courseSelect = $("newStudentCourse");

    const semesterSelect = $("newStudentSemester");

    if (courseSelect) {

        sxFillCourseSelect(courseSelect, "Select course");

        sxLinkCourseSemester(courseSelect, semesterSelect, null);

    }

    const filter = $("studentFilterSemester");

    if (filter) {

        sxFillSelect(filter, "All semesters", (State.snapshot.semesters || []).map(function (s) {

            const course = sxCourseById(s.courseId);

            return { value: s.id, text: (course ? course.name + " · " : "") + s.name };

        }));

    }

}

function sxBadge(text, kind) {

    return sxEl("span", { class: "sx-badge sx-badge-" + kind, text: text });

}

async function renderAdminStudents() {

    const data = await api("staff/students");

    staffStudentsCache = data.students;

    filterAdminStudents();

}

function filterAdminStudents() {

    const holder = $("studentRows");

    if (!holder) {

        return;

    }

    const query = ($("studentSearch").value || "").trim().toLowerCase();

    const semFilter = $("studentFilterSemester").value;

    const students = staffStudentsCache.filter(function (s) {

        const matchesQuery = !query || s.id.toLowerCase().indexOf(query) !== -1

            || s.name.toLowerCase().indexOf(query) !== -1

            || (s.courseName + " " + s.semesterName).toLowerCase().indexOf(query) !== -1;

        const matchesSem = !semFilter || String(s.semesterId) === semFilter;

        return matchesQuery && matchesSem;

    });

    sxClear(holder);

    if (!students.length) {

        holder.appendChild(sxEl("p", { class: "sx-empty", text: "No student matches." }));

        return;

    }

    const isAdmin = State.role === "ADMIN";

    students.forEach(function (student) {

        holder.appendChild(sxEl("div", { class: "admin-table-row student-record sx-student-row" }, [

            sxEl("strong", { text: student.id }),
            sxEl("span", { text: student.name }),
            sxEl("span", { text: student.courseName + " · " + student.semesterName }),
            sxEl("b", { text: sxPercentText(student.attendancePercent) }),

            sxEl("span", { class: "sx-inline-badges" }, [
                sxBadge(student.inside ? "Inside" : "Outside", student.inside ? "ok" : "muted"),
                sxBadge(student.hasFace ? "Face enrolled" : "No face", student.hasFace ? "ok" : "warn")
            ]),

            sxEl("span", { class: "sx-row-actions" }, [

                sxEl("button", {
                    type: "button", class: "sx-btn sx-btn-small", text: student.hasFace ? "Re-enroll face" : "Enroll face",
                    onclick: function () {

                        adminStopScan();

                        sxEnrollFace(student.id, student.name, async function () { await renderAdminStudents(); },
                            async function (descriptor) { await api("staff/students/" + student.id + "/face", { method: "PUT", body: { descriptor: descriptor } }); });

                    }
                }),

                isAdmin ? sxEl("button", {
                    type: "button", class: "sx-btn sx-btn-small sx-btn-ghost", text: "Reset password",
                    onclick: async function () {

                        const password = prompt("New password for " + student.id + " (at least 4 characters):");

                        if (!password) { return; }

                        try {

                            await api("admin/students/" + student.id + "/password", { method: "PUT", body: { password: password } });

                            sxToast("Password reset for " + student.name + ".", "ok");

                        } catch (error) {

                            sxToast(error.message, "bad");

                        }

                    }
                }) : null,

                isAdmin ? sxEl("button", {
                    type: "button", class: "sx-btn sx-btn-small sx-btn-ghost", text: "Remove",
                    onclick: async function () {

                        if (!confirm("Remove " + student.name + " (" + student.id + ")? Their attendance history is removed too.")) {

                            return;

                        }

                        try {

                            await api("admin/students/" + student.id, { method: "DELETE" });

                            sxToast(student.name + " removed.", "info");

                            await renderAdminStudents();

                        } catch (error) {

                            sxToast(error.message, "bad");

                        }

                    }
                }) : null

            ])

        ]));

    });

}

$("studentSearch").addEventListener("input", filterAdminStudents);
$("studentFilterSemester").addEventListener("change", filterAdminStudents);

$("addStudentBtn").addEventListener("click", async function () {

    try {

        await api("admin/students", { method: "POST", body: {

            id: $("newStudentId").value,
            name: $("newStudentName").value,
            courseId: Number($("newStudentCourse").value),
            semesterId: Number($("newStudentSemester").value)

        } });

        $("newStudentId").value = "";
        $("newStudentName").value = "";

        sxToast("Student added.", "ok");

        await renderAdminStudents();

    } catch (error) {

        sxToast(error.message, "bad");

    }

});


/* =====================================================
   STAFF: STUDENT ATTENDANCE LOOKUP + CORRECTIONS
   ===================================================== */

const lookupCalendar = { year: new Date().getFullYear(), month: new Date().getMonth() };
let lookupData = null;
let lookupStudentId = null;

$("lookupStudentBtn").addEventListener("click", function () { doLookup(); });

$("lookupStudentId").addEventListener("keydown", function (e) { if (e.key === "Enter") { doLookup(); } });

async function doLookup() {

    const id = $("lookupStudentId").value.trim().toUpperCase();

    if (!id) {

        return;

    }

    try {

        lookupStudentId = id;

        lookupData = await api("staff/attendance/" + id);

        const now = new Date();

        lookupCalendar.year = now.getFullYear();

        lookupCalendar.month = now.getMonth();

        renderLookupResult();

    } catch (error) {

        $("lookupResult").innerHTML = "";

        $("lookupResult").appendChild(sxEl("p", { class: "sx-empty", text: error.message }));

    }

}

function renderLookupResult() {

    const holder = $("lookupResult");

    sxClear(holder);

    const grid = sxEl("div", { class: "attendance-detail-grid" }, [

        sxEl("div", { class: "detail-stat-card" }, [sxEl("span", { text: "Overall" }), sxEl("strong", { text: sxPercentText(lookupData.percent) })]),
        sxEl("div", { class: "detail-stat-card" }, [sxEl("span", { text: "Full days" }), sxEl("strong", { text: String(lookupData.fullDays) })]),
        sxEl("div", { class: "detail-stat-card" }, [sxEl("span", { text: "Half days" }), sxEl("strong", { text: String(lookupData.halfDays) })]),
        sxEl("div", { class: "detail-stat-card" }, [sxEl("span", { text: "Absent days" }), sxEl("strong", { text: String(lookupData.absentDays) })])

    ]);

    holder.appendChild(grid);

    const calendarCard = sxEl("div", { class: "calendar-card" }, [

        sxEl("div", { class: "calendar-top" }, [
            sxEl("button", { type: "button", text: "←", onclick: function () { shiftLookupMonth(-1); } }),
            sxEl("h2", { id: "lookupCalTitle", text: SX_MONTHS_LONG[lookupCalendar.month] + " " + lookupCalendar.year }),
            sxEl("button", { type: "button", text: "→", onclick: function () { shiftLookupMonth(1); } })
        ]),

        sxEl("div", { class: "calendar-week" }, ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"].map(function (d) { return sxEl("span", { text: d }); })),

        sxEl("div", { class: "calendar-days", id: "lookupCalDays" }),

        sxEl("p", { class: "sx-hint", text: "Click a working day to correct it (full / half / absent)." })

    ]);

    holder.appendChild(calendarCard);

    renderLookupCalendarDays();

}

function shiftLookupMonth(step) {

    const d = new Date(lookupCalendar.year, lookupCalendar.month + step, 1);

    lookupCalendar.year = d.getFullYear();

    lookupCalendar.month = d.getMonth();

    $("lookupCalTitle").textContent = SX_MONTHS_LONG[lookupCalendar.month] + " " + lookupCalendar.year;

    renderLookupCalendarDays();

}

function renderLookupCalendarDays() {

    const holder = $("lookupCalDays");

    if (!holder) {

        return;

    }

    const year = lookupCalendar.year;

    const month = lookupCalendar.month;

    const byDate = {};

    lookupData.days.forEach(function (d) { byDate[d.date] = d; });

    sxClear(holder);

    const offset = (new Date(year, month, 1).getDay() + 6) % 7;

    for (let i = 0; i < offset; i++) {

        holder.appendChild(sxEl("span", { class: "sx-cal-empty", "aria-hidden": "true" }));

    }

    const daysInMonth = new Date(year, month + 1, 0).getDate();

    const todayKey = sxDateKey();

    for (let day = 1; day <= daysInMonth; day++) {

        const key = year + "-" + sxPad(month + 1) + "-" + sxPad(day);

        const entry = byDate[key];

        const classes = [];

        if (key === todayKey) classes.push("calendar-today");

        if (entry) classes.push("sx-day-" + entry.status.toLowerCase());

        if (entry && entry.overridden) classes.push("sx-day-overridden");

        const clickable = entry && key <= todayKey;

        holder.appendChild(sxEl("span", {
            class: classes.join(" ") + (clickable ? " sx-day-clickable" : ""),
            title: entry ? entry.status + (entry.overridden ? " (corrected)" : "") : "",
            text: String(day),
            onclick: clickable ? function () { openDayCorrection(key, entry.status); } : null
        }));

    }

}

async function openDayCorrection(dateKey, currentStatus) {

    const choice = prompt(

        "Set " + lookupStudentId + "'s status for " + sxFormatDateLong(dateKey)

        + " (currently " + currentStatus + ").\nType FULL, HALF, ABSENT, or CLEAR to remove a correction:",

        currentStatus

    );

    if (!choice) {

        return;

    }

    const value = choice.trim().toUpperCase();

    try {

        if (value === "CLEAR") {

            await api("staff/day-override?studentId=" + lookupStudentId + "&date=" + dateKey, { method: "DELETE" });

        } else if (["FULL", "HALF", "ABSENT"].indexOf(value) !== -1) {

            await api("staff/day-override", { method: "POST", body: { studentId: lookupStudentId, date: dateKey, status: value } });

        } else {

            sxToast("Type FULL, HALF, ABSENT or CLEAR.", "warn");

            return;

        }

        lookupData = await api("staff/attendance/" + lookupStudentId);

        renderLookupCalendarDays();

        sxToast("Attendance updated.", "ok");

    } catch (error) {

        sxToast(error.message, "bad");

    }

}


/* =====================================================
   STAFF: OFFICIAL EVENTS
   ===================================================== */

const createEvent = $("createEvent");
const cancelEventEdit = $("cancelEventEdit");
let adminEditingEventId = null;

function sxSetupEventCourseSelect() {

    sxFillSelect($("eventType"), "", (State.snapshot.eventTypes || SX_EVENT_TYPES).map(function (t) { return { value: t, text: t }; }));

    sxFillCourseSelect($("eventCourse"), "All courses");

    $("eventCourse").addEventListener("change", function () {

        const course = sxCourseById(this.value);

        sxFillSelect($("eventYear"), "All years", course ? Array.from({ length: course.years }, function (_, i) {

            return { value: i + 1, text: "Year " + (i + 1) };

        }) : []);

    });

}

const SX_EVENT_TYPES = ["Test", "Exam", "Assignment", "Important Lecture", "Workshop", "Interview", "Placement Event"];

function adminResetEventForm() {

    adminEditingEventId = null;

    $("eventTitle").value = "";
    $("eventDate").value = "";
    $("eventTime").value = "";
    $("eventLocation").value = "";
    $("eventCourse").value = "";
    sxFillSelect($("eventYear"), "All years", []);

    createEvent.textContent = "Create official event";

    cancelEventEdit.hidden = true;

}

createEvent.addEventListener("click", async function () {

    const body = {

        title: $("eventTitle").value,
        type: $("eventType").value,
        date: $("eventDate").value,
        time: $("eventTime").value,
        location: $("eventLocation").value,
        audienceCourseId: $("eventCourse").value ? Number($("eventCourse").value) : null,
        audienceYear: $("eventYear").value ? Number($("eventYear").value) : null

    };

    try {

        if (adminEditingEventId) {

            await api("staff/events/" + adminEditingEventId, { method: "PUT", body: body });

            sxToast("Event updated.", "ok");

        } else {

            await api("staff/events", { method: "POST", body: body });

            sxToast("Event created.", "ok");

        }

        adminResetEventForm();

        State.snapshot = await api("snapshot");

        renderAdminEvents();

        await renderAdminHome();

    } catch (error) {

        sxToast(error.message, "bad");

    }

});

cancelEventEdit.addEventListener("click", adminResetEventForm);

function sxEventMeta(event) {

    const course = event.audienceCourseId ? sxCourseById(event.audienceCourseId) : null;

    return [event.type, sxFormatDateLong(event.date), sxFormatClock(event.time), event.location,
        course ? course.name + (event.audienceYear ? " Year " + event.audienceYear : "") : ""]
        .filter(Boolean).join(" · ");

}

function renderAdminEvents() {

    const list = $("officialEventsList");

    sxClear(list);

    const todayKey = sxDateKey();

    const events = (State.snapshot.events || []).slice().sort(function (a, b) {

        return (a.date + (a.time || "")).localeCompare(b.date + (b.time || ""));

    });

    if (!events.length) {

        list.appendChild(sxEl("p", { class: "sx-empty", text: "No official events yet. Create one above." }));

        return;

    }

    events.forEach(function (event) {

        list.appendChild(sxEl("div", { class: "official-event-row" + (event.date < todayKey ? " sx-past" : "") }, [

            sxEl("div", {}, [sxEl("strong", { text: event.title }), sxEl("p", { text: sxEventMeta(event) })]),

            sxEl("div", { class: "sx-row-actions" }, [

                sxEl("button", {
                    type: "button", text: "Edit", onclick: function () {

                        adminEditingEventId = event.id;

                        $("eventTitle").value = event.title;
                        $("eventType").value = event.type;
                        $("eventDate").value = event.date;
                        $("eventTime").value = event.time || "";
                        $("eventLocation").value = event.location || "";
                        $("eventCourse").value = event.audienceCourseId || "";

                        const course = sxCourseById(event.audienceCourseId);

                        sxFillSelect($("eventYear"), "All years", course ? Array.from({ length: course.years }, function (_, i) {

                            return { value: i + 1, text: "Year " + (i + 1) };

                        }) : []);

                        $("eventYear").value = event.audienceYear || "";

                        createEvent.textContent = "Save changes";

                        cancelEventEdit.hidden = false;

                        $("eventTitle").scrollIntoView({ behavior: "smooth", block: "center" });

                    }
                }),

                sxEl("button", {
                    type: "button", text: "Delete", class: "sx-btn-ghost", onclick: async function () {

                        if (!confirm("Delete \u201c" + event.title + "\u201d?")) { return; }

                        try {

                            await api("staff/events/" + event.id, { method: "DELETE" });

                            if (adminEditingEventId === event.id) { adminResetEventForm(); }

                            State.snapshot = await api("snapshot");

                            sxToast("Event deleted.", "info");

                            renderAdminEvents();

                        } catch (error) {

                            sxToast(error.message, "bad");

                        }

                    }
                })

            ])

        ]));

    });

}


/* =====================================================
   STAFF: HOME + REPORTS
   ===================================================== */

async function renderAdminHome() {

    sxSetText("adminStatStudents", String((await api("staff/students")).students.length));

    const today = await api("staff/presence/today");

    const inside = today.visits.filter(function (v) { return !v.exit && !v.incomplete; }).length;

    sxSetText("adminStatInside", String(inside));

    sxSetText("adminStatToday", String(today.visits.length));

    const target = State.snapshot.settings.targetPercent;

    const low = await api("staff/reports/low");

    sxSetText("adminStatLow", String(low.students.length));

    sxSetText("adminStatLowSub", "Under " + target + "% attendance");

    const rows = $("adminRecentRows");

    sxClear(rows);

    const recent = today.visits.slice(-6).reverse();

    if (!recent.length) {

        rows.appendChild(sxEl("p", { class: "sx-empty", text: "No scans yet today." }));

    }

    recent.forEach(function (v) {

        rows.appendChild(sxEl("div", { class: "admin-table-row" }, [

            sxEl("strong", { text: v.studentName }),
            sxEl("span", { text: sxFormatTime(v.entry) }),
            sxEl("span", { text: v.exit ? sxFormatTime(v.exit) : "—" }),
            sxEl("b", { class: "attendance-marked", text: v.exit ? "LEFT" : "INSIDE" })

        ]));

    });

    const list = $("adminHomeEvents");

    sxClear(list);

    const todayKey = sxDateKey();

    const upcoming = (State.snapshot.events || []).filter(function (e) { return e.date >= todayKey; })

        .sort(function (a, b) { return (a.date + (a.time || "")).localeCompare(b.date + (b.time || "")); }).slice(0, 3);

    if (!upcoming.length) {

        list.appendChild(sxEl("p", { class: "sx-empty", text: "No upcoming events." }));

    }

    upcoming.forEach(function (event) {

        const date = sxParseDateKey(event.date);

        list.appendChild(sxEl("div", { class: "admin-event" }, [

            sxEl("div", { class: "admin-event-date" }, [String(date.getDate()), sxEl("small", { text: SX_MONTHS_SHORT[date.getMonth()] })]),
            sxEl("div", {}, [sxEl("strong", { text: event.title }), sxEl("p", { text: sxEventMeta(event) })]),
            sxEl("span", { text: event.type.toUpperCase() })

        ]));

    });

}

async function renderAdminReports() {

    const low = await api("staff/reports/low");

    const target = State.snapshot.settings.targetPercent;

    const all = await api("staff/students");

    const withData = all.students.filter(function (s) { return s.attendancePercent !== null; });

    const average = withData.length

        ? Math.round(withData.reduce(function (sum, s) { return sum + s.attendancePercent; }, 0) / withData.length)

        : null;

    sxSetText("reportAvg", average === null ? "—" : average + "%");

    sxSetText("reportLow", String(low.students.length));

    sxSetText("reportEvents", String((State.snapshot.events || []).length));

    const rows = $("reportLowRows");

    sxClear(rows);

    if (!low.students.length) {

        rows.appendChild(sxEl("p", { class: "sx-empty", text: "Every student is at or above " + target + "%." }));

    }

    low.students.sort(function (a, b) { return (a.percent || 0) - (b.percent || 0); }).forEach(function (s) {

        rows.appendChild(sxEl("div", { class: "admin-table-row" }, [

            sxEl("strong", { text: s.name }),
            sxEl("span", { text: s.courseName + " · " + s.semesterName }),
            sxEl("b", { text: sxPercentText(s.percent) }),
            sxEl("span", { text: s.fullDays + " full, " + s.halfDays + " half, " + s.absentDays + " absent" })

        ]));

    });

}


/* =====================================================
   ADMIN SETTINGS: courses, timings, calendar, staff, reset
   ===================================================== */

function renderAdminSettings() {

    if (State.role !== "ADMIN") {

        return;

    }

    renderCourseList();
    renderTimingSelects();
    renderTimingList();
    renderWorkingDaysPicker();
    renderCalendarDayList();
    renderStaffList();

}


/* ---------- courses / semesters / subjects ---------- */

function renderCourseList() {

    const holder = $("courseList");

    sxClear(holder);

    (State.snapshot.courses || []).forEach(function (course) {

        const semesters = sxSemestersForCourse(course.id);

        const card = sxEl("div", { class: "sx-course-card" }, [

            sxEl("div", { class: "sx-course-head" }, [

                sxEl("strong", { text: course.name + " (" + course.years + " year" + (course.years === 1 ? "" : "s") + ")" }),

                sxEl("button", {
                    type: "button", class: "sx-btn sx-btn-small sx-btn-ghost", text: "Delete course",
                    onclick: async function () {

                        if (!confirm("Delete " + course.name + "?")) { return; }

                        try {

                            await api("admin/courses/" + course.id, { method: "DELETE" });

                            State.snapshot = await api("snapshot");

                            renderAdminSettings();

                            sxSetupStaffOnlyUi();

                            sxSetupRegisterCourseSelects();

                            sxSetupStudentCourseSelects();

                            sxSetupEventCourseSelect();

                        } catch (error) {

                            sxToast(error.message, "bad");

                        }

                    }
                })

            ])

        ]);

        semesters.forEach(function (sem) {

            const subjects = sxSubjectsForSemester(sem.id);

            const semBox = sxEl("div", { class: "sx-semester-box" }, [

                sxEl("div", { class: "sx-semester-head" }, [

                    sxEl("span", { text: sem.name + " · Year " + sem.yearNo }),

                    sxEl("button", {
                        type: "button", class: "sx-icon-btn", "aria-label": "Delete semester", text: "✕",
                        onclick: async function () {

                            if (!confirm("Delete " + sem.name + "?")) { return; }

                            try {

                                await api("admin/semesters/" + sem.id, { method: "DELETE" });

                                State.snapshot = await api("snapshot");

                                renderAdminSettings();

                            } catch (error) {

                                sxToast(error.message, "bad");

                            }

                        }
                    })

                ]),

                sxEl("div", { class: "sx-subject-tags" }, subjects.map(function (subject) {

                    return sxEl("span", { class: "sx-subject-tag" }, [

                        subject.name,

                        sxEl("button", {
                            type: "button", "aria-label": "Delete subject", text: "✕",
                            onclick: async function () {

                                try {

                                    await api("admin/subjects/" + subject.id, { method: "DELETE" });

                                    State.snapshot = await api("snapshot");

                                    renderAdminSettings();

                                } catch (error) {

                                    sxToast(error.message, "bad");

                                }

                            }
                        })

                    ]);

                })),

                sxEl("div", { class: "sx-form-row" }, [

                    sxEl("input", { class: "sx-input sx-input-small", type: "text", placeholder: "New subject", id: "newSubjectName_" + sem.id }),

                    sxEl("button", {
                        type: "button", class: "sx-btn sx-btn-small", text: "Add subject",
                        onclick: async function () {

                            const input = $("newSubjectName_" + sem.id);

                            if (!input.value.trim()) { return; }

                            try {

                                await api("admin/subjects", { method: "POST", body: { semesterId: sem.id, name: input.value } });

                                State.snapshot = await api("snapshot");

                                renderAdminSettings();

                            } catch (error) {

                                sxToast(error.message, "bad");

                            }

                        }
                    })

                ])

            ]);

            card.appendChild(semBox);

        });

        card.appendChild(sxEl("div", { class: "sx-form-row", style: "margin-top:10px" }, [

            sxEl("input", { class: "sx-input sx-input-small", type: "text", placeholder: "New semester name", id: "newSemName_" + course.id }),

            sxEl("input", { class: "sx-input sx-input-small", type: "number", placeholder: "Year", min: "1", max: String(course.years), id: "newSemYear_" + course.id, style: "max-width:80px" }),

            sxEl("button", {
                type: "button", class: "sx-btn sx-btn-small", text: "Add semester",
                onclick: async function () {

                    const nameInput = $("newSemName_" + course.id);

                    const yearInput = $("newSemYear_" + course.id);

                    if (!nameInput.value.trim() || !yearInput.value) { return; }

                    try {

                        await api("admin/semesters", { method: "POST", body: { courseId: course.id, name: nameInput.value, yearNo: Number(yearInput.value) } });

                        State.snapshot = await api("snapshot");

                        renderAdminSettings();

                    } catch (error) {

                        sxToast(error.message, "bad");

                    }

                }
            })

        ]));

        holder.appendChild(card);

    });

}

$("addCourseBtn").addEventListener("click", async function () {

    const name = $("newCourseName").value.trim();

    const years = Number($("newCourseYears").value) || 3;

    if (!name) {

        return;

    }

    try {

        await api("admin/courses", { method: "POST", body: { name: name, years: years } });

        $("newCourseName").value = "";

        State.snapshot = await api("snapshot");

        renderAdminSettings();

        sxSetupRegisterCourseSelects();

        sxSetupStudentCourseSelects();

        sxSetupEventCourseSelect();

        sxToast("Course added.", "ok");

    } catch (error) {

        sxToast(error.message, "bad");

    }

});


/* ---------- timings ---------- */

function renderTimingSelects() {

    sxFillCourseSelect($("timingCourse"), "All courses");

    $("timingCourse").addEventListener("change", function () {

        const course = sxCourseById(this.value);

        sxFillSelect($("timingYear"), "All years", course ? Array.from({ length: course.years }, function (_, i) {

            return { value: i + 1, text: "Year " + (i + 1) };

        }) : []);

    });

    $("timingFrom").value = sxDateKey();

}

function renderTimingList() {

    const holder = $("timingList");

    sxClear(holder);

    const timings = State.snapshot.timings || [];

    if (!timings.length) {

        holder.appendChild(sxEl("p", { class: "sx-empty", text: "No timings set. The default is 14:50 for everyone." }));

        return;

    }

    timings.forEach(function (t) {

        const course = t.courseId ? sxCourseById(t.courseId) : null;

        holder.appendChild(sxEl("div", { class: "official-event-row" }, [

            sxEl("div", {}, [

                sxEl("strong", { text: "Half-day at " + sxFormatClock(t.halfDayTime) }),

                sxEl("p", { text: "From " + sxFormatDateLong(t.effectiveFrom) + " · " +

                    (course ? course.name + (t.yearNo ? " Year " + t.yearNo : " (all years)") : "All courses") })

            ]),

            sxEl("button", {
                type: "button", class: "sx-btn-ghost", text: "Delete", onclick: async function () {

                    try {

                        await api("admin/timings/" + t.id, { method: "DELETE" });

                        State.snapshot = await api("snapshot");

                        renderTimingList();

                    } catch (error) {

                        sxToast(error.message, "bad");

                    }

                }
            })

        ]));

    });

}

$("addTimingBtn").addEventListener("click", async function () {

    try {

        await api("admin/timings", { method: "POST", body: {

            effectiveFrom: $("timingFrom").value,
            halfDayTime: $("timingHalfDay").value,
            courseId: $("timingCourse").value ? Number($("timingCourse").value) : null,
            yearNo: $("timingYear").value ? Number($("timingYear").value) : null

        } });

        State.snapshot = await api("snapshot");

        renderTimingList();

        sxToast("Timing added.", "ok");

    } catch (error) {

        sxToast(error.message, "bad");

    }

});


/* ---------- working days ---------- */

function renderWorkingDaysPicker() {

    const holder = $("workingDaysPicker");

    sxClear(holder);

    const active = new Set(State.snapshot.settings.workingDays || []);

    const labels = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];

    labels.forEach(function (label, i) {

        const dayNo = i + 1;

        const button = sxEl("button", {
            type: "button",
            class: "sx-day-chip" + (active.has(dayNo) ? " sx-active" : ""),
            text: label,
            "data-day": dayNo
        });

        button.addEventListener("click", function () { this.classList.toggle("sx-active"); });

        holder.appendChild(button);

    });

}

$("saveWorkingDaysBtn").addEventListener("click", async function () {

    const days = Array.from(document.querySelectorAll("#workingDaysPicker .sx-active")).map(function (b) { return Number(b.dataset.day); });

    try {

        await api("admin/settings", { method: "PUT", body: { workingDays: days } });

        State.snapshot = await api("snapshot");

        sxToast("Working days saved.", "ok");

    } catch (error) {

        sxToast(error.message, "bad");

    }

});


/* ---------- calendar (holidays / special days) ---------- */

$("calDayKind").addEventListener("change", function () {

    $("calDayEndTime").hidden = this.value !== "SPECIAL";

});

function renderCalendarDayList() {

    const holder = $("calDayList");

    sxClear(holder);

    const days = (State.snapshot.calendarDays || []).filter(function (d) { return d.date >= sxDateKey(sxAddDays(new Date(), -14)); })

        .sort(function (a, b) { return a.date.localeCompare(b.date); });

    if (!days.length) {

        holder.appendChild(sxEl("p", { class: "sx-empty", text: "No holidays or special days set." }));

        return;

    }

    days.forEach(function (d) {

        holder.appendChild(sxEl("div", { class: "official-event-row" }, [

            sxEl("div", {}, [

                sxEl("strong", { text: sxFormatDateLong(d.date) + " · " + (d.kind === "HOLIDAY" ? "Holiday" : "Special day, ends " + sxFormatClock(d.endTime)) }),

                sxEl("p", { text: d.note || "" })

            ]),

            sxEl("button", {
                type: "button", class: "sx-btn-ghost", text: "Delete", onclick: async function () {

                    try {

                        await api("admin/calendar/" + d.id, { method: "DELETE" });

                        State.snapshot = await api("snapshot");

                        renderCalendarDayList();

                    } catch (error) {

                        sxToast(error.message, "bad");

                    }

                }
            })

        ]));

    });

}

$("addCalDayBtn").addEventListener("click", async function () {

    try {

        await api("admin/calendar", { method: "POST", body: {

            date: $("calDayDate").value,
            kind: $("calDayKind").value,
            endTime: $("calDayEndTime").value,
            note: $("calDayNote").value

        } });

        State.snapshot = await api("snapshot");

        renderCalendarDayList();

        sxToast("Added to the calendar.", "ok");

    } catch (error) {

        sxToast(error.message, "bad");

    }

});


/* ---------- staff logins ---------- */

async function renderStaffList() {

    const holder = $("staffListBox");

    sxClear(holder);

    let list;

    try {

        list = (await api("admin/staff")).staff;

    } catch (error) {

        holder.appendChild(sxEl("p", { class: "sx-empty", text: error.message }));

        return;

    }

    list.forEach(function (u) {

        holder.appendChild(sxEl("div", { class: "official-event-row" }, [

            sxEl("div", {}, [sxEl("strong", { text: u.displayName + " (" + u.username + ")" }), sxEl("p", { text: u.role })]),

            u.role === "TEACHER" ? sxEl("button", {
                type: "button", class: "sx-btn-ghost", text: "Remove", onclick: async function () {

                    if (!confirm("Remove login for " + u.username + "?")) { return; }

                    try {

                        await api("admin/staff/" + u.id, { method: "DELETE" });

                        renderStaffList();

                        sxToast("Login removed.", "info");

                    } catch (error) {

                        sxToast(error.message, "bad");

                    }

                }
            }) : null

        ]));

    });

}

$("addStaffBtn").addEventListener("click", async function () {

    try {

        await api("admin/staff", { method: "POST", body: {

            username: $("newStaffUsername").value,
            name: $("newStaffName").value,
            role: $("newStaffRole").value,
            password: $("newStaffPassword").value

        } });

        $("newStaffUsername").value = "";
        $("newStaffName").value = "";
        $("newStaffPassword").value = "";

        renderStaffList();

        sxToast("Login created.", "ok");

    } catch (error) {

        sxToast(error.message, "bad");

    }

});


/* ---------- reset demo data ---------- */

$("resetDemoData").addEventListener("click", async function () {

    if (!confirm("Reset SCANX to the demo data? Courses, students, staff logins (except yours), sessions and events will be replaced.")) {

        return;

    }

    try {

        await api("admin/reset-demo", { method: "POST" });

        sxToast("Demo data restored.", "info");

        await sxLoadApp();

    } catch (error) {

        sxToast(error.message, "bad");

    }

});


/* =====================================================
   STAFF: BELL
   ===================================================== */

async function sxRefreshStaffBell() {

    const dot = $("staffBellDot");

    if (!State.snapshot || State.role === "STUDENT") {

        return;

    }

    try {

        const [today, low] = await Promise.all([api("staff/presence/today"), api("staff/reports/low")]);

        dot.hidden = (today.visits.length + low.students.length) === 0;

    } catch (error) {

        // background refresh; ignore
    }

}

$("staffBell").addEventListener("click", async function () {

    const panel = $("staffBellPanel");

    panel.hidden = !panel.hidden;

    if (panel.hidden) {

        return;

    }

    sxClear(panel);

    panel.appendChild(sxEl("p", { class: "sx-panel-title", text: "Loading…" }));

    try {

        const [today, low] = await Promise.all([api("staff/presence/today"), api("staff/reports/low")]);

        sxClear(panel);

        panel.appendChild(sxEl("p", { class: "sx-panel-title", text: "Today" }));

        panel.appendChild(sxEl("div", { class: "sx-notify-row" }, [

            sxEl("strong", { text: today.visits.length + " gate scan" + (today.visits.length === 1 ? "" : "s") }),
            sxEl("span", { text: today.visits.filter(function (v) { return !v.exit; }).length + " still inside" })

        ]));

        if (low.students.length) {

            panel.appendChild(sxEl("p", { class: "sx-panel-title", text: "Below target" }));

            low.students.slice(0, 4).forEach(function (s) {

                panel.appendChild(sxEl("div", { class: "sx-notify-row" }, [sxEl("strong", { text: s.name }), sxEl("span", { text: sxPercentText(s.percent) })]));

            });

        }

    } catch (error) {

        sxClear(panel);

        panel.appendChild(sxEl("p", { class: "sx-empty", text: error.message }));

    }

    $("staffBellDot").hidden = true;

});


/* =====================================================
   LANDING PAGE: demo scan animation + "how it works" steps
   ===================================================== */

function startScanning() {

    const scanner = $("scannerArea");

    const status = $("scanStatus");

    const success = $("success");

    if (!scanner || !status || !success) {

        return;

    }

    scanner.classList.add("scanning");

    success.classList.remove("show");

    status.textContent = "Scanning…";

    setTimeout(function () {

        scanner.classList.remove("scanning");

        status.textContent = "Student ID detected ✓";

        success.classList.add("show");

    }, 2000);

}

const steps = document.querySelectorAll(".step");

const stepData = [

    { number: "01", title: "Scan at the gate", description: "Scan a QR code, or use your face, when you enter and leave college.", icon: "◇" },
    { number: "02", title: "Take the register", description: "In class, a teacher ticks who's present, subject by subject.", icon: "▤" },
    { number: "03", title: "Track your progress", description: "See day and subject attendance, and understand exactly where you stand.", icon: "◔" },
    { number: "04", title: "Plan what comes next", description: "Find out how many more full days you need to reach your attendance target.", icon: "↗" }

];

steps.forEach(function (step) {

    step.addEventListener("click", function () {

        const index = Number(step.getAttribute("data-step"));

        steps.forEach(function (item) { item.classList.remove("active"); });

        step.classList.add("active");

        $("stepTitle").textContent = stepData[index].title;

        $("stepDescription").textContent = stepData[index].description;

        $("stepIcon").textContent = stepData[index].icon;

        $("displayNumber").textContent = stepData[index].number;

        const display = $("stepDisplay");

        display.classList.remove("change");

        void display.offsetWidth;

        display.classList.add("change");

    });

});
