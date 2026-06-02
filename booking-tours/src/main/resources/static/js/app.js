// ================================================================
// SUN Booking Tours — Global JS
// ================================================================

// Auto-dismiss flash alerts sau 4 giây
document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('.alert.alert-dismissible').forEach(alert => {
        setTimeout(() => {
            const bsAlert = bootstrap.Alert.getOrCreateInstance(alert);
            bsAlert.close();
        }, 4000);
    });
});

// Toggle password visibility
function togglePassword(inputId, btn) {
    const input = document.getElementById(inputId);
    if (!input) return;
    const icon = btn.querySelector('i');
    if (input.type === 'password') {
        input.type = 'text';
        icon.classList.replace('bi-eye', 'bi-eye-slash');
    } else {
        input.type = 'password';
        icon.classList.replace('bi-eye-slash', 'bi-eye');
    }
}

// Handle broken image sources — replaces obsolete inline onerror attributes.
// Uses capture phase (true) because the 'error' event does not bubble.
// Supported data attributes:
//   data-img-error="fallback"  → hide the <img>, reveal the nearest .img-error-fallback sibling
//   data-img-error="hide"      → hide the <img>, show the element with id = data-fallback-id (display:flex)
document.addEventListener('error', function (e) {
    const img = e.target;
    if (img.tagName !== 'IMG') return;

    const mode = img.dataset.imgError;
    if (!mode) return;

    if (mode === 'fallback') {
        img.style.display = 'none';
        const fallbackIcon = img.closest('.position-relative')
            && img.closest('.position-relative').querySelector('.img-error-fallback');
        if (fallbackIcon) {
            fallbackIcon.classList.remove('d-none');
        }
    } else if (mode === 'hide') {
        img.style.display = 'none';
        const fallbackId = img.dataset.fallbackId;
        if (fallbackId) {
            const fallbackEl = document.getElementById(fallbackId);
            if (fallbackEl) fallbackEl.style.display = 'flex';
        }
    }
}, true);
