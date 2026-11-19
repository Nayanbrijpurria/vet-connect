import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import vm from "node:vm";

const source = await readFile(new URL("../js/video-consultation.js", import.meta.url), "utf8");

class Element {
    constructor(id = "") {
        this.id = id;
        this.children = [];
        this.listeners = new Map();
        this.attributes = new Map();
        this.hidden = id === "enable-audio";
        this.disabled = false;
        this.textContent = "";
        this.srcObject = null;
        this.childElementCount = 0;
    }

    addEventListener(type, callback) {
        this.listeners.set(type, callback);
    }

    setAttribute(name, value) {
        this.attributes.set(name, value);
    }

    appendChild(child) {
        child.parentNode = this;
        this.children.push(child);
        this.childElementCount = this.children.length;
        return child;
    }

    replaceChildren(...children) {
        this.children.forEach(child => { child.parentNode = null; });
        this.children = [];
        children.forEach(child => this.appendChild(child));
        this.childElementCount = this.children.length;
    }

    remove() {
        if (!this.parentNode) return;
        this.parentNode.children = this.parentNode.children.filter(child => child !== this);
        this.parentNode.childElementCount = this.parentNode.children.length;
        this.parentNode = null;
    }

    async click() {
        return this.listeners.get("click")?.({ currentTarget: this });
    }
}

class FakeMediaTrack {
    constructor(kind) {
        this.kind = kind;
        this.enabled = true;
        this.stopCount = 0;
        this.attachedElement = null;
    }

    stop() {
        this.stopCount += 1;
    }

    attach(element = new Element(`track-${this.kind}`)) {
        this.attachedElement = element;
        if (this.rejectFirstPlay) element.failFirstPlay = true;
        element.play = async () => {
            if (element.failFirstPlay) {
                element.failFirstPlay = false;
                this.rejectFirstPlay = false;
                throw new Error("Autoplay blocked");
            }
        };
        return element;
    }

    detach() {
        this.attachedElement?.remove();
        return this.attachedElement ? [this.attachedElement] : [];
    }
}

class FakeMediaStream {
    constructor(tracks = []) {
        this.tracks = tracks;
    }
    getTracks() { return this.tracks; }
    getAudioTracks() { return this.tracks.filter(track => track.kind === "audio"); }
    getVideoTracks() { return this.tracks.filter(track => track.kind === "video"); }
}

const roomEvents = {
    ConnectionStateChanged: "ConnectionStateChanged",
    Reconnecting: "Reconnecting",
    Reconnected: "Reconnected",
    TrackSubscribed: "TrackSubscribed",
    TrackUnsubscribed: "TrackUnsubscribed",
    ParticipantDisconnected: "ParticipantDisconnected",
    Disconnected: "Disconnected"
};

function boot({ role = "CUSTOMER", failMicrophone = false } = {}) {
    const ids = [
        "video-status", "waiting-room", "call-room", "preview-video", "local-video",
        "remote-video-container", "remote-audio-container", "join-call", "enable-audio",
        "video-back", "video-title", "toggle-preview-mic", "toggle-preview-camera",
        "toggle-mic", "toggle-camera", "leave-call"
    ];
    const elements = new Map(ids.map(id => [id, new Element(id)]));
    const fetches = [];
    const rooms = [];
    const navigations = [];
    let ready;
    let mediaId = 0;
    let failMicrophoneOnce = failMicrophone;

    class FakeRoom {
        constructor() {
            this.handlers = new Map();
            this.disconnected = false;
            this.cameraStates = [];
            this.microphoneStates = [];
            this.audioStarts = 0;
            this.localParticipant = {
                trackPublications: new Map(),
                cameraStates: [],
                microphoneStates: [],
                async setCameraEnabled(enabled) {
                    this.cameraStates.push(enabled);
                    if (enabled) {
                        const track = new FakeMediaTrack("video");
                        this.trackPublications.set("camera", {
                            track: {
                                mediaStreamTrack: track,
                                stop() { track.stop(); },
                                detach() {}
                            }
                        });
                    } else {
                        this.trackPublications.delete("camera");
                    }
                },
                async setMicrophoneEnabled(enabled) {
                    this.microphoneStates.push(enabled);
                    if (failMicrophoneOnce && enabled) {
                        failMicrophoneOnce = false;
                        throw new Error("Microphone permission failed");
                    }
                    if (enabled) {
                        const track = new FakeMediaTrack("audio");
                        this.trackPublications.set("microphone", {
                            track: {
                                mediaStreamTrack: track,
                                stop() { track.stop(); },
                                detach() {}
                            }
                        });
                    } else {
                        this.trackPublications.delete("microphone");
                    }
                },
                getTrackPublication(source) {
                    return this.trackPublications.get(source);
                }
            };
            rooms.push(this);
        }

        on(event, handler) {
            this.handlers.set(event, handler);
            return this;
        }
        async connect(url, token) {
            this.url = url;
            this.token = token;
        }
        async disconnect() {
            if (this.disconnected) return;
            this.disconnected = true;
            this.handlers.get(roomEvents.Disconnected)?.();
        }
        async startAudio() { this.audioStarts += 1; }
        emit(event, ...args) { this.handlers.get(event)?.(...args); }
    }

    const document = {
        addEventListener(type, handler) {
            if (type === "DOMContentLoaded") ready = handler;
        },
        getElementById(id) { return elements.get(id); },
        createElement(tag) {
            const element = new Element(tag);
            element.tagName = tag;
            return element;
        }
    };
    const window = {
        location: {
            search: "?appointmentId=42",
            replace: value => navigations.push(value)
        },
        vetApi: { async ensureSession() { return true; } },
        LivekitClient: {
            Room: FakeRoom,
            RoomEvent: roomEvents,
            Track: { Source: { Camera: "camera", Microphone: "microphone" } }
        },
        addEventListener() {}
    };
    const navigator = {
        mediaDevices: {
            async getUserMedia(constraints) {
                mediaId += 1;
                const kind = constraints.audio ? "audio" : "video";
                return new FakeMediaStream([new FakeMediaTrack(kind)]);
            }
        }
    };

    vm.runInNewContext(source, {
        window,
        document,
        navigator,
        sessionStorage: { getItem: key => key === "vetplatform_role" ? role : null },
        API_BASE_URL: "",
        URLSearchParams,
        MediaStream: FakeMediaStream,
        Map,
        Object,
        Boolean,
        Promise,
        console,
        fetch: async (url, options) => {
            fetches.push({ url, options });
            return {
                ok: true,
                async json() {
                    return {
                        serverUrl: "wss://livekit.example",
                        participantToken: "short-lived",
                        roomName: "opaque-room"
                    };
                }
            };
        }
    });

    return {
        elements,
        fetches,
        rooms,
        navigations,
        async ready() { await ready(); },
        get mediaId() { return mediaId; }
    };
}

test("remote audio playback prompts for interaction and tracks clean up on unsubscribe/disconnect", async () => {
    const app = boot();
    await app.ready();
    await app.elements.get("join-call").click();

    const room = app.rooms[0];
    const participant = { identity: "vet-7" };
    const audioTrack = new FakeMediaTrack("audio");
    audioTrack.rejectFirstPlay = true;
    room.emit(roomEvents.TrackSubscribed, audioTrack, {}, participant);
    room.emit(roomEvents.TrackSubscribed, audioTrack, {}, participant);
    const secondAudioTrack = new FakeMediaTrack("audio");
    room.emit(roomEvents.TrackSubscribed, secondAudioTrack, {}, participant);
    const remoteVideoTrack = new FakeMediaTrack("video");
    room.emit(roomEvents.TrackSubscribed, remoteVideoTrack, {}, participant);
    await new Promise(resolve => setImmediate(resolve));

    assert.equal(app.elements.get("remote-audio-container").children.length, 2);
    assert.equal(app.elements.get("remote-video-container").children.length, 1);
    assert.equal(app.elements.get("enable-audio").hidden, false);
    assert.match(app.elements.get("video-status").textContent, /Enable consultation audio/);
    await app.elements.get("enable-audio").click();
    assert.equal(room.audioStarts, 1);
    assert.equal(app.elements.get("enable-audio").hidden, true);

    room.emit(roomEvents.TrackUnsubscribed, audioTrack);
    assert.equal(app.elements.get("remote-audio-container").children.length, 1);
    room.emit(roomEvents.ParticipantDisconnected, participant);
    assert.equal(app.elements.get("remote-audio-container").children.length, 0);
    assert.equal(app.elements.get("remote-video-container").children.length, 0);
});

test("camera and microphone disabled in the preview remain disabled after joining", async () => {
    const app = boot();
    await app.ready();
    await app.elements.get("toggle-preview-mic").click();
    await app.elements.get("toggle-preview-camera").click();
    await app.elements.get("join-call").click();

    assert.deepEqual(app.rooms[0].localParticipant.cameraStates, [false]);
    assert.deepEqual(app.rooms[0].localParticipant.microphoneStates, [false]);
    assert.equal(app.elements.get("local-video").srcObject, null);
});

test("media permission failure disconnects, stops acquired tracks and permits a retry", async () => {
    const app = boot({ failMicrophone: true });
    await app.ready();
    await app.elements.get("join-call").click();

    assert.equal(app.rooms[0].disconnected, true);
    assert.equal(app.elements.get("call-room").hidden, true);
    assert.equal(app.elements.get("join-call").disabled, false);
    assert.match(app.elements.get("video-status").textContent, /Microphone permission failed/);
    assert.equal(
        app.rooms[0].localParticipant.trackPublications.get("camera").track.mediaStreamTrack.stopCount,
        1
    );

    await app.elements.get("join-call").click();
    assert.equal(app.rooms.length, 2);
    assert.equal(app.elements.get("call-room").hidden, false);
});

test("leaving disconnects and returns to appointments without ending the shared session", async () => {
    const app = boot();
    await app.ready();
    await app.elements.get("join-call").click();
    const room = app.rooms[0];

    await app.elements.get("leave-call").click();
    await app.elements.get("leave-call").click();

    assert.equal(room.disconnected, true);
    assert.equal(app.fetches.filter(request => request.url.includes("/video/end")).length, 0);
    assert.deepEqual(app.navigations, ["appointments.html"]);
    assert.equal(app.elements.get("remote-audio-container").children.length, 0);
});
