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
