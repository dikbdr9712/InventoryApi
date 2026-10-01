// authNav.js — Fixed for all pages: Index.html, Contact.html, etc.
(function () {
    function updateNavbarForAuth() {
    const topLoginLink = document.getElementById('loginLink');
    const slideLoginLink = document.getElementById('slideLoginLink');

    // Wait if either is missing (e.g., on pages without slideNav)
    if ((!topLoginLink && !slideLoginLink)) {
        setTimeout(updateNavbarForAuth, 150);
        return;
    }

    const isLoggedIn = localStorage.getItem('isLoggedIn') === 'true';
    const userRole = localStorage.getItem('userRole');
    const allowedRoles = ["ADMIN", "MANAGER", "CONTROLLER"];
    const hasAccess = allowedRoles.includes(userRole);

    // Update top navbar
    if (topLoginLink) {
        topLoginLink.textContent = isLoggedIn ? 'Profile' : 'Login / Register';
        topLoginLink.href = isLoggedIn ? 'profile.html' : 'login.html';
    }

    // Update slide-in menu
    if (slideLoginLink) {
        if (isLoggedIn) {
            slideLoginLink.style.display = 'none';
        } else {
            slideLoginLink.style.display = '';
            slideLoginLink.textContent = 'Login / Register';
            slideLoginLink.href = 'login.html';
        }
    }

    // Handle admin dropdown (only in top navbar)
    const navbarNav = document.getElementById('navbarNav');
    const navList = navbarNav ? navbarNav.querySelector('.navbar-nav') : null;

    if (navList) {
        const existingDropdown = document.getElementById('admin-dropdown');
        if (existingDropdown) existingDropdown.remove();

        if (isLoggedIn && hasAccess) {
            const dropdownLi = document.createElement('li');
            dropdownLi.id = 'admin-dropdown';
            dropdownLi.className = 'nav-item dropdown';

            dropdownLi.innerHTML = `
                <a class="nav-link dropdown-toggle text-dark font-weight-bold" 
                   href="#" 
                   id="adminDropdown" 
                   role="button" 
                   data-toggle="dropdown" 
                   aria-haspopup="true" 
                   aria-expanded="false"
                   style="padding-right: 0.75rem;">
                  <i class="fas fa-cogs me-1"></i> Admin
                </a>
                <div class="dropdown-menu dropdown-menu-right" aria-labelledby="adminDropdown">
                  <a class="dropdown-item" href="pos.html"><i class="fas fa-shopping-cart mr-2"></i> Point of Sale</a>
                  <a class="dropdown-item" href="pos-history.html"><i class="fas fa-history mr-2"></i> POS Sales History</a>
                  <a class="dropdown-item" href="SalesDashboard.html"><i class="fas fa-chart-line mr-2"></i> Sales Dashboard</a>
                  <a class="dropdown-item" href="order-list.html"><i class="fas fa-list mr-2"></i> Order List</a>
                  <a class="dropdown-item" href="OrderVerification.html"><i class="fas fa-check-circle mr-2"></i> Verify Payments</a>
                  <a class="dropdown-item" href="users.html"><i class="fas fa-user mr-2"></i> User Management</a>
                </div>
            `;
            navList.appendChild(dropdownLi);
        }
    }

    // Optional: Hide/show other admin elements
    const adminBtnContainer = document.getElementById('adminButtonContainer');
    if (adminBtnContainer) {
        adminBtnContainer.style.display = (isLoggedIn && hasAccess) ? 'block' : 'none';
    }
    const slideAdminSection = document.getElementById('slideAdminSection');
        if (slideAdminSection) {
        slideAdminSection.style.display = (isLoggedIn && hasAccess) ? 'block' : 'none';
        }
}

    // Run after DOM is ready + give Bootstrap time to initialize
    function runWhenReady() {
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', () => {
                setTimeout(updateNavbarForAuth, 400);
            });
        } else {
            setTimeout(updateNavbarForAuth, 300);
        }
    }

    // Initial run
    runWhenReady();

    // Re-run after full page load (images, fonts, etc.)
    window.addEventListener('load', () => {
        setTimeout(updateNavbarForAuth, 200);
    });

    // Re-run on SPA-like navigation (if you ever add it)
    window.addEventListener('hashchange', updateNavbarForAuth);
})();