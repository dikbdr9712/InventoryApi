document.addEventListener('DOMContentLoaded', () => {
  const hamburger = document.querySelector('.navbar-toggler');
  const slideNav = document.getElementById('slideNav');
  const closeBtn = document.querySelector('.slide-close');

  const toggleSlideNav = () => {
    slideNav.classList.toggle('active');
    // Optional: prevent scrolling behind overlay
    document.body.style.overflow = slideNav.classList.contains('active') ? 'hidden' : '';
  };

  if (hamburger) {
    hamburger.addEventListener('click', toggleSlideNav);
  }

  if (closeBtn) {
    closeBtn.addEventListener('click', toggleSlideNav);
  }

  // Close on link click (optional but recommended)
  slideNav.querySelectorAll('a').forEach(link => {
    link.addEventListener('click', () => {
      slideNav.classList.remove('active');
      document.body.style.overflow = '';
    });
  });
});