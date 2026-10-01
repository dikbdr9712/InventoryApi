// Global escape function
function escapeHtml(text) {
  if (!text) return '';
  return text
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}

// Global functions
window.openRestock = function(sku, name) {
  if (!sku || !name) {
    alert("Cannot restock: missing SKU or item name.");
    return;
  }
  const url = `restock.html?sku=${encodeURIComponent(sku)}&itemName=${encodeURIComponent(name)}`;
  window.open(url, '_blank');
};

window.openNewItemForm = function() {
  window.open('new-item.html', '_blank');
};

window.addToCart = function(product) {
  if (product.stock <= 0) {
    alert("❌ This item is out of stock.");
    return;
  }
  let cart = JSON.parse(localStorage.getItem('cart')) || [];
  const existing = cart.find(item => item.id === product.id);
  if (existing) {
    existing.quantity += 1;
  } else {
    cart.push({
      id: product.id,
      name: product.name,
      price: product.price,
      image: product.image,
      quantity: 1
    });
  }
  localStorage.setItem('cart', JSON.stringify(cart));
  alert(`✅ "${product.name}" added to cart!`);
  updateCartBadge();
};

function updateCartBadge() {
  const cart = JSON.parse(localStorage.getItem('cart')) || [];
  const totalItems = cart.reduce((sum, item) => sum + (item.quantity || 1), 0);
  const badge = document.getElementById('cart-count-badge');
  if (badge) {
    if (totalItems > 0) {
      badge.textContent = totalItems;
      badge.classList.add('show');
    } else {
      badge.classList.remove('show');
    }
  }
}

// Main logic
document.addEventListener('DOMContentLoaded', () => {
  const cardGallery = document.getElementById("card-gallery-wrapper");
  let allItems = [];

  function renderCards(items) {
    cardGallery.innerHTML = '';
    const userRole = localStorage.getItem('userRole');
    const canRestock = ['ADMIN', 'MANAGER', 'CONTROLLER'].includes(userRole);

    items.forEach(item => {
      let imageUrl = '../Images/default.jpg';
      if (item.imagePath && item.imagePath.trim()) {
        imageUrl = `http://localhost:8080${item.imagePath}`;
      } else {
        const imageName = item.itemName.toLowerCase().replace(/\s+/g, '') + '.jpg';
        imageUrl = `../Images/${imageName}`;
      }

      const safeItemName = escapeHtml(item.itemName);
      const safeSku = escapeHtml(item.sku || '');

      // Extract MRP and Selling Price
      const mrp = item.mrp != null ? parseFloat(item.mrp) : null;
      const sellingPrice = item.sellingPrice != null ? parseFloat(item.sellingPrice) : null;

      // Calculate discount %
      let discountPercent = null;
      if (mrp && sellingPrice !== null && sellingPrice < mrp) {
        discountPercent = Math.round(((mrp - sellingPrice) / mrp) * 100);
      }

      // Build discount badge
      let discountBadgeHtml = '';
      if (discountPercent !== null) {
        discountBadgeHtml = `<span class="discount-badge">Save ${discountPercent}%</span>`;
      }

      // Restock button
      let restockBtnHtml = '';
      if (canRestock) {
        if (item.sku && item.sku.trim()) {
          restockBtnHtml = `
            <button class="restock-btn" onclick="event.stopPropagation(); openRestock('${safeSku}', '${safeItemName}')">
              Restock
            </button>
          `;
        } else {
          restockBtnHtml = `
            <button class="restock-btn" disabled title="Item has no SKU. Edit item first to assign one.">
              No SKU
            </button>
          `;
        }
      }

      // Price display
      const displaySelling = sellingPrice !== null ? sellingPrice.toFixed(2) : 'N/A';
      const originalPriceHtml = (mrp && sellingPrice !== null && sellingPrice < mrp)
        ? `<span class="original-price">Nu. ${mrp.toFixed(2)}</span>`
        : '';

		let discountLabelHtml = '';
		if (discountPercent !== null) {
		  discountLabelHtml = `<div class="discount-label">${discountPercent}% off</div>`;
		}
		
		const card = `
		  <li data-group="0" onclick="showProductDetail(${item.itemId})">
		    <div class="product-card">
		      <img src="${imageUrl}" alt="${safeItemName}" class="product-image" 
		           onerror="this.src='../Images/default.jpg'">
		      <h3 class="product-title">${safeItemName}</h3>
		      <div class="product-price">
		        Nu. ${displaySelling}
		        ${originalPriceHtml}
		      </div>
		      ${discountLabelHtml}
		      ${restockBtnHtml}
		    </div>
		  </li>
		`;
      cardGallery.innerHTML += card;
    });
  }

  window.showProductDetail = function(itemId) {
    const item = allItems.find(i => i.itemId === itemId);
    if (!item) return;

    document.getElementById('detail-title').textContent = item.itemName;

    let detailImageUrl = '../Images/default.jpg';
    if (item.imagePath && item.imagePath.trim()) {
      detailImageUrl = `http://localhost:8080${item.imagePath}`;
    } else {
      const imageName = item.itemName.toLowerCase().replace(/\s+/g, '') + '.jpg';
      detailImageUrl = `../Images/${imageName}`;
    }

    document.getElementById('detail-image').src = detailImageUrl;
    document.getElementById('detail-desc').textContent = item.description || '';
    
    // Show selling price
    const sellingPrice = item.sellingPrice != null ? parseFloat(item.sellingPrice) : null;
    document.getElementById('detail-price').textContent = sellingPrice !== null ? `Nu. ${sellingPrice.toFixed(2)}` : 'N/A';

    // Optional: Show MRP in detail view (add span in HTML)
    const mrp = item.mrp != null ? parseFloat(item.mrp) : null;
    const originalPriceEl = document.getElementById('detail-original-price');
    const discountEl = document.getElementById('detail-discount');
    if (mrp && sellingPrice !== null && sellingPrice < mrp) {
    // Show strikethrough MRP
    originalPriceEl.textContent = `Nu. ${mrp.toFixed(2)}`;
    originalPriceEl.style.display = 'inline';

    // Show discount %
    const discountPercent = Math.round(((mrp - sellingPrice) / mrp) * 100);
    discountEl.textContent = `${discountPercent}% off`;
    discountEl.style.display = 'inline';
  } else {
    originalPriceEl.style.display = 'none';
    discountEl.style.display = 'none';
  }

    document.getElementById('detail-stock').textContent = item.currentStock || 'N/A';
    document.getElementById('detail-uom').textContent = item.uom || '';
    
    const statusEl = document.getElementById('detail-status');
    statusEl.textContent = item.availability || 'Unavailable';
    statusEl.className = (item.availability === 'Available') ? 'status-available' : 'status-unavailable';

    // Rebind Add to Cart
    const btn = document.getElementById('add-to-cart-btn');
    btn.replaceWith(btn.cloneNode(true));
    document.getElementById('add-to-cart-btn').onclick = () => {
      addToCart({
        id: item.itemId,
        name: item.itemName,
        price: item.sellingPrice,
        image: detailImageUrl,
        stock: item.currentStock
      });
    };

    document.getElementById('buy-button').href = `payment.html?itemId=${itemId}`;

    const canEdit = ['ADMIN', 'MANAGER'].includes(localStorage.getItem('userRole'));
    document.getElementById('edit-item-btn').style.display = canEdit ? 'inline-block' : 'none';
    document.getElementById('edit-item-btn').href = `edit-item.html?itemId=${itemId}`;

    document.getElementById('container').style.display = 'none';
    document.getElementById('product-detail').style.display = 'block';
  };

  window.showProducts = function() {
    document.getElementById('product-detail').style.display = 'none';
    document.getElementById('container').style.display = 'block';
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  async function init() {
    try {
      const res = await fetch('http://localhost:8080/api/items/allItems');
      if (res.status === 401) {
        alert("⚠️ You need to log in to view products.");
        window.location.href = 'login.html';
        return;
      }
      if (!res.ok) throw new Error(await res.text());
      
      allItems = await res.json();
      renderCards(allItems);

      document.getElementById('search-products').addEventListener('input', (e) => {
        const term = e.target.value.toLowerCase().trim();
        if (!term) {
          renderCards(allItems);
        } else {
          const filtered = allItems.filter(item =>
            (item.itemName || '').toLowerCase().includes(term) ||
            (item.description || '').toLowerCase().includes(term) ||
            (item.sku || '').toLowerCase().includes(term) ||
            (item.category || '').toLowerCase().includes(term)
          );
          renderCards(filtered);
        }
      });

      const addBtn = document.getElementById('addNewItemBtn');
      const role = localStorage.getItem('userRole');
      addBtn.style.display = (['ADMIN', 'MANAGER'].includes(role)) ? 'inline-block' : 'none';
      addBtn?.addEventListener('click', window.openNewItemForm);

    } catch (err) {
      console.error(err);
      cardGallery.innerHTML = `<p style="color:red;">⚠️ Failed to load products.</p>`;
    }
  }

  init();
  updateCartBadge();
});

// Nav auth update
setTimeout(() => {
  const loginLink = document.getElementById('loginLink');
  const navList = document.querySelector('.navbar-nav.ml-auto');
  
  if (loginLink && navList) {
    const isLoggedIn = localStorage.getItem('isLoggedIn') === 'true';
    const userName = localStorage.getItem('userName');
    
    if (isLoggedIn && userName) {
      loginLink.textContent = 'My Account';
      loginLink.href = 'profile.html';
      
      const userRole = localStorage.getItem('userRole')?.toUpperCase() || '';
      const allowedRoles = ["ADMIN", "MANAGER", "CONTROLLER"];
      if (allowedRoles.includes(userRole)) {
        const existing = document.getElementById('admin-dropdown');
        if (!existing) {
          const dropdownLi = document.createElement('li');
          dropdownLi.id = 'admin-dropdown';
          dropdownLi.className = 'nav-item dropdown';
          dropdownLi.innerHTML = `
            <a class="nav-link dropdown-toggle text-dark" href="#" id="adminDropdown" role="button" 
              data-toggle="dropdown" aria-haspopup="true" aria-expanded="false">
              <i class="fas fa-cogs"></i> Admin
            </a>
            <div class="dropdown-menu" aria-labelledby="adminDropdown">
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
    }
  }
}, 800);