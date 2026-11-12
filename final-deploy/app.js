// SOUMYA AI Assistant landing — APK availability check.
// The APK is placed at /soumya-ai.apk by the release coordinator.
// Until then, download buttons show a clear "coming soon" note instead of a dead link.
(function () {
  var notes = [document.getElementById('apkNote'), document.getElementById('apkNote2')];
  var buttons = [
    document.getElementById('heroDownload'),
    document.getElementById('navDownload'),
    document.getElementById('footerDownload')
  ];

  function markPending() {
    notes.forEach(function (n) {
      if (!n) return;
      n.classList.add('pending');
      n.textContent = '⏳ APK v1.0.0 is being built — the Download button will work as soon as the release APK is published here. Check back shortly.';
    });
    buttons.forEach(function (b) {
      if (!b) return;
      b.addEventListener('click', function (e) {
        e.preventDefault();
        alert('APK v1.0.0 is still being built. The download will be live here as soon as the release APK is published — please check back shortly.');
      });
    });
  }

  function markReady(sizeMB) {
    notes.forEach(function (n) {
      if (!n) return;
      n.textContent = 'APK v1.0.0 · Android 10+ · ' + sizeMB + ' · Free, no account, no ads.';
    });
  }

  // HEAD the APK: ready ONLY if it is really an APK (not the Pages SPA fallback,
  // which serves index.html with 200 for any missing path).
  fetch('/soumya-ai.apk', { method: 'HEAD', cache: 'no-store' })
    .then(function (r) {
      var ct = (r.headers.get('content-type') || '').toLowerCase();
      var len = parseInt(r.headers.get('content-length') || '0', 10);
      var isApk = r.ok && len > 1000000 &&
        (ct.indexOf('android.package-archive') !== -1 || ct.indexOf('octet-stream') !== -1);
      if (!isApk) return markPending();
      markReady((len / 1048576).toFixed(1) + ' MB');
    })
    .catch(markPending);
})();
