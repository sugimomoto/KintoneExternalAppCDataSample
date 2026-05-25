// Adapter Console - SSE で active-adapters をブラウザの全テーブル行に反映する。
(function () {
    const table = document.getElementById("active-adapters-table");
    if (!table || typeof EventSource === "undefined") return;

    const evtSource = new EventSource("/runtime/sse");
    evtSource.addEventListener("active-adapters", (e) => {
        try {
            const data = JSON.parse(e.data);
            const active = new Map(data.map((a) => [a.tableName, a]));
            document.querySelectorAll("tr[data-table]").forEach((row) => {
                const name = row.dataset.table;
                const a = active.get(name);
                const statusCell = row.querySelector(".status");
                if (!statusCell) return;
                if (a) {
                    statusCell.innerHTML = `<small class="status-badge serving">●Running on ${a.port}</small>`;
                } else {
                    statusCell.innerHTML = `<small class="status-badge stopped">○Stopped</small>`;
                }
            });
        } catch (err) {
            console.warn("SSE parse error", err);
        }
    });
    window.addEventListener("beforeunload", () => evtSource.close());
})();
