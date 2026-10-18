/**
 * Sequel Web Portal — Interactive Documentation & Visualizer
 */

document.addEventListener('DOMContentLoaded', () => {
    initArchitectureTabs();
    initSchemaTabs();
    initCopyButtons();
    initSyncSimulator();
});

/* ── Architecture Explorer Tabs ─────────────────────────────────── */
function initArchitectureTabs() {
    const tabBtns = document.querySelectorAll('.arch-tab-btn');
    const panels = document.querySelectorAll('.arch-panel');

    tabBtns.forEach(btn => {
        btn.addEventListener('click', () => {
            const targetId = btn.getAttribute('data-target');

            // Deactivate all
            tabBtns.forEach(b => b.classList.remove('active'));
            panels.forEach(p => p.classList.remove('active'));

            // Activate clicked
            btn.classList.add('active');
            const targetPanel = document.getElementById(targetId);
            if (targetPanel) {
                targetPanel.classList.add('active');
            }
        });
    });
}

/* ── Database Schema DDL Tabs ───────────────────────────────────── */
function initSchemaTabs() {
    const schemaBtns = document.querySelectorAll('.schema-tab-btn');
    const schemaPanels = document.querySelectorAll('.schema-panel');

    schemaBtns.forEach(btn => {
        btn.addEventListener('click', () => {
            const targetId = btn.getAttribute('data-schema-target');

            schemaBtns.forEach(b => b.classList.remove('active'));
            schemaPanels.forEach(p => p.classList.remove('active'));

            btn.classList.add('active');
            const targetPanel = document.getElementById(targetId);
            if (targetPanel) {
                targetPanel.classList.add('active');
            }
        });
    });
}

/* ── Copy to Clipboard with Toast ───────────────────────────────── */
function initCopyButtons() {
    const copyBtns = document.querySelectorAll('.copy-btn');
    const toast = document.getElementById('toast');

    copyBtns.forEach(btn => {
        btn.addEventListener('click', () => {
            const targetSelector = btn.getAttribute('data-copy-target');
            const codeElem = document.querySelector(targetSelector);

            if (codeElem) {
                const text = codeElem.innerText;
                navigator.clipboard.writeText(text).then(() => {
                    showToast('Copied to clipboard!');
                    btn.textContent = 'Copied!';
                    setTimeout(() => {
                        btn.textContent = 'Copy';
                    }, 2000);
                }).catch(() => {
                    showToast('Failed to copy');
                });
            }
        });
    });
}

function showToast(message) {
    const toast = document.getElementById('toast');
    if (!toast) return;

    toast.textContent = message;
    toast.classList.add('show');

    setTimeout(() => {
        toast.classList.remove('show');
    }, 2500);
}

/* ── Sync Engine State Machine Simulator ────────────────────────── */
function initSyncSimulator() {
    const triggerBtn = document.getElementById('sim-trigger-btn');
    const statusBadge = document.getElementById('sim-status-badge');
    const logOutput = document.getElementById('sim-log-output');

    if (!triggerBtn || !statusBadge || !logOutput) return;

    triggerBtn.addEventListener('click', () => {
        triggerBtn.disabled = true;
        triggerBtn.textContent = 'Processing Sync...';

        // Stage 1: Local write
        statusBadge.textContent = 'PENDING_INSERT';
        statusBadge.className = 'spec-value gold';
        logOutput.innerHTML = '<span style="color:#FFD700">[T+0ms]</span> User tapped "Mark Watched". Row written to Room SQLite in 4ms.<br>';

        // Stage 2: WorkManager enqueue
        setTimeout(() => {
            logOutput.innerHTML += '<span style="color:#A855F7">[T+20ms]</span> WorkManager enqueued SyncWatchedWorker (ExistingWorkPolicy.APPEND_OR_REPLACE).<br>';
        }, 300);

        // Stage 3: Network transmission
        setTimeout(() => {
            logOutput.innerHTML += '<span style="color:#60A5FA">[T+250ms]</span> Supabase PostgREST batch upsert dispatched with JWT bearer token.<br>';
        }, 700);

        // Stage 4: Cloud confirmation
        setTimeout(() => {
            statusBadge.textContent = 'SYNCED';
            statusBadge.className = 'spec-value teal';
            logOutput.innerHTML += '<span style="color:#03DAC6">[T+650ms]</span> HTTP 201 Created. UUID assigned. Local sync_status upgraded to SYNCED.<br>';
            triggerBtn.disabled = false;
            triggerBtn.textContent = 'Simulate Another Action';
        }, 1400);
    });
}
