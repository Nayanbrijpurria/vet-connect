
document.addEventListener("DOMContentLoaded", () => {
    const form = document.getElementById("medical-record-form");
    const message = document.getElementById("record-message");
    const list = document.getElementById("medical-record-list");
    const summary = document.getElementById("record-summary");
    const fileInput = document.getElementById("record-file");

    if (!form || !message || !list || !summary || !fileInput) return;

    const DB_NAME = "VetPlatformMedicalRecords";
    const STORE_NAME = "documents";
    const MAX_FILE_SIZE = 10 * 1024 * 1024;

    function openDatabase() {
        return new Promise((resolve, reject) => {
            const request = indexedDB.open(DB_NAME, 1);

            request.onupgradeneeded = () => {
                const db = request.result;

                if (!db.objectStoreNames.contains(STORE_NAME)) {
                    db.createObjectStore(STORE_NAME, {
                        keyPath: "id"
                    });
                }
            };

            request.onsuccess = () => resolve(request.result);
            request.onerror = () => reject(request.error);
        });
    }

    async function saveDocument(record) {
        const db = await openDatabase();

        return new Promise((resolve, reject) => {
            const transaction = db.transaction(STORE_NAME, "readwrite");
            transaction.objectStore(STORE_NAME).put(record);

            transaction.oncomplete = () => {
                db.close();
                resolve();
            };

            transaction.onerror = () => {
                db.close();
                reject(transaction.error);
            };

            transaction.onabort = () => {
                db.close();
                reject(transaction.error);
            };
        });
    }

    async function getDocuments() {
        const db = await openDatabase();

        return new Promise((resolve, reject) => {
            const transaction = db.transaction(STORE_NAME, "readonly");
            const request = transaction.objectStore(STORE_NAME).getAll();

            request.onsuccess = () => resolve(request.result || []);
            request.onerror = () => reject(request.error);

            transaction.oncomplete = () => db.close();
            transaction.onerror = () => db.close();
        });
    }

    async function deleteDocument(id) {
        const db = await openDatabase();

        return new Promise((resolve, reject) => {
            const transaction = db.transaction(STORE_NAME, "readwrite");
            transaction.objectStore(STORE_NAME).delete(id);

            transaction.oncomplete = () => {
                db.close();
                resolve();
            };

            transaction.onerror = () => {
                db.close();
                reject(transaction.error);
            };
        });
    }

    function makeElement(tag, className, text) {
        const element = document.createElement(tag);

        if (className) element.className = className;
        if (text !== undefined) element.textContent = text;

        return element;
    }

    function formatSize(bytes) {
        if (bytes < 1024) return `${bytes} B`;
        if (bytes < 1024 * 1024) {
            return `${(bytes / 1024).toFixed(1)} KB`;
        }

        return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
    }

    async function renderDocuments() {
        try {
            const documents = await getDocuments();
            list.replaceChildren();

            summary.textContent =
                `${documents.length} document${documents.length === 1 ? "" : "s"} saved in this browser`;

            if (documents.length === 0) {
                list.appendChild(
                    makeElement("p", "", "No medical documents added yet.")
                );
                return;
            }

            documents.sort((a, b) => b.createdAt - a.createdAt);

            for (const record of documents) {
                const card = makeElement("article", "medical-record-card");
                const details = makeElement("div", "medical-record-details");

                details.appendChild(makeElement("h3", "", record.fileName));
                details.appendChild(
                    makeElement("span", "record-badge", record.type)
                );
                details.appendChild(
                    makeElement("p", "", `Pet: ${record.petName}`)
                );
                details.appendChild(
                    makeElement("p", "", `Document date: ${record.date}`)
                );
                details.appendChild(
                    makeElement("p", "", `File size: ${formatSize(record.size)}`)
                );

                const actions = makeElement("div", "medical-record-actions");

                const previewButton = makeElement("button", "", "Preview");
                previewButton.type = "button";

                previewButton.addEventListener("click", async () => {
                    try {
                        const documents = await getDocuments();
                        const selected = documents.find(item => item.id === record.id);

                        if (!selected) {
                            message.textContent = "Document not found.";
                            return;
                        }

                        const url = URL.createObjectURL(selected.file);
                        const previewWindow = window.open(url, "_blank");

                        if (previewWindow) {
                            previewWindow.opener = null;
                            setTimeout(() => URL.revokeObjectURL(url), 60000);
                        } else {
                            URL.revokeObjectURL(url);
                            message.textContent =
                                "Preview was blocked. Allow pop-ups and try again.";
                        }
                    } catch {
                        message.textContent = "Could not preview this document.";
                    }
                });

                const deleteButton = makeElement("button", "", "Delete");
                deleteButton.type = "button";

                deleteButton.addEventListener("click", async () => {
                    const confirmed = window.confirm(
                        `Delete the demo record "${record.fileName}"?`
                    );

                    if (!confirmed) return;

                    try {
                        await deleteDocument(record.id);
                        message.textContent = "Demo document deleted.";
                        await renderDocuments();
                    } catch {
                        message.textContent = "Could not delete the document.";
                    }
                });

                actions.append(previewButton, deleteButton);
                card.append(details, actions);
                list.appendChild(card);
            }
        } catch {
            message.textContent =
                "Browser storage is unavailable. Please use a supported browser.";
        }
    }

    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        message.textContent = "";

        if (!form.reportValidity()) return;

        const file = fileInput.files[0];

        if (!file) {
            message.textContent = "Please select a document.";
            return;
        }

        if (file.size > MAX_FILE_SIZE) {
            message.textContent = "The file must be 10 MB or smaller.";
            return;
        }

        const allowedTypes = [
            "application/pdf",
            "image/jpeg",
            "image/png",
            "image/webp",
            "text/plain"
        ];

        if (!allowedTypes.includes(file.type)) {
            message.textContent =
                "Unsupported file type. Choose a PDF, JPG, PNG, WEBP or TXT file.";
            return;
        }

        const record = {
            id: crypto.randomUUID(),
            petName: form.elements.petName.value.trim(),
            type: form.elements.recordType.value,
            date: form.elements.recordDate.value,
            fileName: file.name,
            size: file.size,
            file: file,
            createdAt: Date.now()
        };

        if (!record.petName || !record.date) {
            message.textContent = "Please complete all required fields.";
            return;
        }

        try {
            await saveDocument(record);
            form.reset();
            message.textContent =
                "Document saved in this browser. It has not been uploaded to VetPlatform's server.";
            await renderDocuments();
        } catch {
            message.textContent =
                "Could not save this file. Try a smaller file or another browser.";
        }
    });

    renderDocuments();
});
