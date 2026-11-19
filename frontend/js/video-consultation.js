document.addEventListener("DOMContentLoaded", async () => {
    "use strict";

    const status = document.getElementById("video-status");
    const waitingRoom = document.getElementById("waiting-room");
    const callRoom = document.getElementById("call-room");
    const previewVideo = document.getElementById("preview-video");
    const localVideo = document.getElementById("local-video");
    const remoteVideoContainer = document.getElementById("remote-video-container");
    const remoteAudioContainer = document.getElementById("remote-audio-container");
    const joinButton = document.getElementById("join-call");
    const enableAudioButton = document.getElementById("enable-audio");
    const appointmentId = new URLSearchParams(window.location.search).get("appointmentId");
    const role = sessionStorage.getItem("vetplatform_role");
    const remoteTrackElements = new Map();
    const remoteAudioElements = new Map();

    let room = null;
    let joining = false;
    let leaving = false;
    let cleanupPromise = null;
    let previewStream = null;
    let previewAudioTrack = null;
    let previewVideoTrack = null;
    let micEnabled = true;
    let cameraEnabled = true;

    const sessionRestored = await window.vetApi.ensureSession();
    if (!sessionRestored || !appointmentId || !["CUSTOMER", "VET"].includes(role)) {
        window.location.replace("login.html");
        return;
    }

    const returnPage = role === "VET" ? "vet-dashboard.html" : "appointments.html";
    document.getElementById("video-back").href = returnPage;
    document.getElementById("video-title").textContent = `Appointment #${appointmentId}`;

    function setStatus(message) {
        status.textContent = message;
    }

    function updateControl(buttonId, enabled, enabledText, disabledText, available = true) {
        const button = document.getElementById(buttonId);
        button.disabled = !available;
        button.textContent = enabled ? enabledText : disabledText;
        button.setAttribute("aria-pressed", String(enabled));
    }

    function updatePreviewControls() {
        updateControl("toggle-preview-mic", micEnabled, "Mute microphone", "Unmute microphone", Boolean(previewAudioTrack));
        updateControl("toggle-preview-camera", cameraEnabled, "Turn camera off", "Turn camera on", Boolean(previewVideoTrack));
    }

    async function readResponse(response) {
        const data = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(data.error || data.message || "The video request failed.");
        return data;
    }

    async function startPreview() {
        const permissionIssues = [];
        previewAudioTrack = null;
        previewVideoTrack = null;

        try {
            const audioStream = await navigator.mediaDevices.getUserMedia({ audio: true, video: false });
            previewAudioTrack = audioStream.getAudioTracks()[0] || null;
        } catch (_) {
            permissionIssues.push("microphone unavailable");
        }

        try {
            const videoStream = await navigator.mediaDevices.getUserMedia({ audio: false, video: true });
            previewVideoTrack = videoStream.getVideoTracks()[0] || null;
        } catch (_) {
            permissionIssues.push("camera unavailable");
        }

        micEnabled = Boolean(previewAudioTrack);
        cameraEnabled = Boolean(previewVideoTrack);
        previewStream = new MediaStream(
            [previewAudioTrack, previewVideoTrack].filter(Boolean)
        );
        previewVideo.srcObject = previewStream;
        updatePreviewControls();

        if (permissionIssues.length) {
            setStatus(`${permissionIssues.join(" and ")}. You can still join without it.`);
        } else {
            setStatus("Camera and microphone preview ready.");
        }
    }

    async function restartPreviewPreservingSettings() {
        const wantedMic = micEnabled;
        const wantedCamera = cameraEnabled;
        await startPreview();
        if (!wantedMic && previewAudioTrack) {
            micEnabled = false;
            previewAudioTrack.enabled = false;
        }
        if (!wantedCamera && previewVideoTrack) {
            cameraEnabled = false;
            previewVideoTrack.enabled = false;
        }
        updatePreviewControls();
    }

    function stopPreview() {
        if (previewStream) {
            previewStream.getTracks().forEach(track => {
                try {
                    track.stop();
                } catch (_) {
                    // Track cleanup is best-effort so repeated cleanup remains safe.
                }
            });
        }
        previewStream = null;
        previewAudioTrack = null;
        previewVideoTrack = null;
        previewVideo.srcObject = null;
    }

    function detachRemoteTrack(track) {
        const attachment = remoteTrackElements.get(track);
        if (!attachment) return;

        try {
            track.detach();
        } catch (_) {
            // The track may already have been detached by the SDK.
        }

        attachment.element.remove();
        if (attachment.wrapper && attachment.wrapper.childElementCount === 0) {
            attachment.wrapper.remove();
        }
        remoteTrackElements.delete(track);
        remoteAudioElements.delete(track);
    }

    function removeParticipantTracks(participantIdentity) {
        for (const [track, attachment] of remoteTrackElements) {
            if (attachment.participantIdentity === participantIdentity) {
                detachRemoteTrack(track);
            }
        }
    }

    function syncLocalVideo() {
        const publication = room?.localParticipant?.getTrackPublication(
            window.LivekitClient.Track.Source.Camera
        );
        if (cameraEnabled && publication?.track) {
            localVideo.srcObject = new MediaStream([publication.track.mediaStreamTrack]);
        } else {
            localVideo.srcObject = null;
        }
    }

    async function cleanupRoom() {
        if (cleanupPromise) return cleanupPromise;

        cleanupPromise = (async () => {
            const activeRoom = room;
            room = null;
            joining = false;

            if (activeRoom) {
                try {
                    await activeRoom.disconnect();
                } catch (_) {
                    // Continue local cleanup even when the network disconnect fails.
                }
                const publications = activeRoom.localParticipant?.trackPublications;
                const values = publications instanceof Map
                    ? [...publications.values()]
                    : Object.values(publications || {});
                values.forEach(publication => {
                    try {
                        publication.track?.stop();
                        publication.track?.detach();
                    } catch (_) {
                        // Continue cleaning the remaining resources.
                    }
                });
            }

            for (const track of [...remoteTrackElements.keys()]) {
                detachRemoteTrack(track);
            }
            remoteAudioContainer.replaceChildren();
            remoteVideoContainer.replaceChildren();
            localVideo.srcObject = null;
            stopPreview();
            callRoom.hidden = true;
            waitingRoom.hidden = false;
            joinButton.disabled = false;
            enableAudioButton.hidden = true;
        })().finally(() => {
            cleanupPromise = null;
        });

        return cleanupPromise;
    }

    function attachRemoteTrack(track, participant) {
        if (remoteTrackElements.has(track)) return;

        const participantIdentity = participant.identity;
        if (track.kind === "audio") {
            const element = track.attach();
            element.autoplay = true;
            element.playsInline = true;
            element.setAttribute("aria-label", "Remote consultation audio");
            remoteAudioContainer.appendChild(element);
            const attachment = { element, participantIdentity };
            remoteTrackElements.set(track, attachment);
            remoteAudioElements.set(track, element);
            Promise.resolve(element.play()).catch(() => {
                enableAudioButton.hidden = false;
                setStatus("Audio playback is blocked. Select “Enable consultation audio” to hear the other participant.");
            });
            return;
        }

        if (track.kind === "video") {
            const wrapper = document.createElement("article");
            wrapper.className = "video-tile";
            wrapper.setAttribute("aria-label", "Other participant video");
            const element = track.attach();
            element.autoplay = true;
            element.playsInline = true;
            wrapper.appendChild(element);
            remoteVideoContainer.appendChild(wrapper);
            remoteTrackElements.set(track, {
                element,
                wrapper,
                participantIdentity
            });
        }
    }

    async function togglePreviewTrack(kind) {
        if (kind === "audio" && previewAudioTrack) {
            micEnabled = !micEnabled;
            previewAudioTrack.enabled = micEnabled;
        } else if (kind === "video" && previewVideoTrack) {
            cameraEnabled = !cameraEnabled;
            previewVideoTrack.enabled = cameraEnabled;
        }
        updatePreviewControls();
    }

    document.getElementById("toggle-preview-mic")
        .addEventListener("click", () => togglePreviewTrack("audio"));
    document.getElementById("toggle-preview-camera")
        .addEventListener("click", () => togglePreviewTrack("video"));

    joinButton.addEventListener("click", async () => {
        if (joining || room) return;
        joining = true;
        joinButton.disabled = true;
        setStatus("Checking authorization and preparing the room...");

        try {
            const response = await fetch(
                `${API_BASE_URL}/api/appointments/${encodeURIComponent(appointmentId)}/video/join`,
                {
                    method: "POST",
                    headers: {
                        Authorization: "Bearer",
                        "X-VetConnect-CSRF": "1",
                        "Content-Type": "application/json"
                    }
                }
            );
            const join = await readResponse(response);
            if (!window.LivekitClient) throw new Error("The LiveKit client could not be loaded.");

            const activeRoom = new window.LivekitClient.Room({
                adaptiveStream: true,
                dynacast: true
            });
            room = activeRoom;
            activeRoom.on(window.LivekitClient.RoomEvent.ConnectionStateChanged, state => {
                if (room === activeRoom) setStatus(`Connection: ${state}`);
            });
            activeRoom.on(window.LivekitClient.RoomEvent.Reconnecting, () => {
                if (room === activeRoom) setStatus("Connection interrupted. Reconnecting...");
            });
            activeRoom.on(window.LivekitClient.RoomEvent.Reconnected, () => {
                if (room === activeRoom) setStatus("Reconnected to the consultation.");
            });
            activeRoom.on(window.LivekitClient.RoomEvent.TrackSubscribed, (track, publication, participant) => {
                attachRemoteTrack(track, participant);
            });
            activeRoom.on(window.LivekitClient.RoomEvent.TrackUnsubscribed, track => {
                detachRemoteTrack(track);
            });
            activeRoom.on(window.LivekitClient.RoomEvent.ParticipantDisconnected, participant => {
                removeParticipantTracks(participant.identity);
                setStatus("The other participant disconnected.");
            });
            activeRoom.on(window.LivekitClient.RoomEvent.Disconnected, () => {
                if (room === activeRoom && !leaving) {
                    void (async () => {
                        await cleanupRoom();
                        await restartPreviewPreservingSettings();
                        setStatus("Disconnected from the consultation. You can safely try joining again.");
                    })();
                }
            });

            await activeRoom.connect(join.serverUrl, join.participantToken);
            stopPreview();
            await activeRoom.localParticipant.setCameraEnabled(cameraEnabled);
            await activeRoom.localParticipant.setMicrophoneEnabled(micEnabled);

            joining = false;
            waitingRoom.hidden = true;
            callRoom.hidden = false;
            syncLocalVideo();
            updateControl("toggle-mic", micEnabled, "Mute microphone", "Unmute microphone", true);
            updateControl("toggle-camera", cameraEnabled, "Turn camera off", "Turn camera on", true);
            setStatus("Connected. Waiting for the other participant.");
        } catch (error) {
            const message = error.message || "Unable to join the consultation.";
            await cleanupRoom();
            await restartPreviewPreservingSettings();
            setStatus(message);
        }
    });

    document.getElementById("toggle-mic").addEventListener("click", async event => {
        if (!room) return;
        const nextEnabled = !micEnabled;
        try {
            await room.localParticipant.setMicrophoneEnabled(nextEnabled);
            micEnabled = nextEnabled;
            updateControl("toggle-mic", micEnabled, "Mute microphone", "Unmute microphone");
        } catch (_) {
            setStatus("Unable to change microphone state.");
        }
    });

    document.getElementById("toggle-camera").addEventListener("click", async event => {
        if (!room) return;
        const nextEnabled = !cameraEnabled;
        try {
            await room.localParticipant.setCameraEnabled(nextEnabled);
            cameraEnabled = nextEnabled;
            syncLocalVideo();
            updateControl("toggle-camera", cameraEnabled, "Turn camera off", "Turn camera on");
        } catch (_) {
            setStatus("Unable to change camera state.");
        }
    });

    enableAudioButton.addEventListener("click", async () => {
        if (!room) return;
        try {
            await room.startAudio();
            for (const audio of remoteAudioElements.values()) await audio.play();
            enableAudioButton.hidden = true;
            setStatus("Consultation audio enabled.");
        } catch (_) {
            setStatus("Audio is still blocked. Check your browser's sound permissions and try again.");
        }
    });

    document.getElementById("leave-call").addEventListener("click", async () => {
        if (leaving) return;
        leaving = true;
        await cleanupRoom();
        window.location.replace(returnPage);
    });

    window.addEventListener("beforeunload", () => {
        void cleanupRoom();
    });

    if (!navigator.mediaDevices?.getUserMedia || typeof MediaStream === "undefined") {
        setStatus("This browser does not support camera and microphone access.");
        joinButton.disabled = true;
        return;
    }
    await startPreview();
});
