let cart = [];
let allItems = [];
let currentOrder = null;
let highlightedIndex = -1;
let appliedTaxes = [{ type: 'GST', rate: 5, manuallyEdited: false }];

function showError(message) {
  alert(`❌ Error: ${message}`);
}

function showSuccess(message) {
  alert(message);
}

async function loadItems() {
  try {
    const res = await fetch('http://localhost:8080/api/items/allItems', { credentials: 'include' });
    if (!res.ok) throw new Error('Failed to load items');
    
    allItems = await res.json();
    allItems = allItems.map(item => ({
      ...item,
      itemName: item.itemName || 'Unknown Item',
      mrp: item.mrp != null ? parseFloat(item.mrp) : 0.00,
      sellingPrice: item.sellingPrice != null ? parseFloat(item.sellingPrice) : 0.00,
      itemCode: item.sku || item.barcode || ''
    }));
  } catch (e) {
    alert('Error loading items: ' + e.message);
    console.error(e);
  }
}

function debounce(func, wait) {
  let timeout;
  return function executedFunction(...args) {
    const later = () => {
      clearTimeout(timeout);
      func(...args);
    };
    clearTimeout(timeout);
    timeout = setTimeout(later, wait);
  };
}

document.addEventListener('DOMContentLoaded', () => {
  const itemSearch = document.getElementById('itemSearch');
  if (itemSearch) itemSearch.focus();

  const debouncedSearch = debounce(handleSearchInput, 200);
  if (itemSearch) itemSearch.addEventListener('input', debouncedSearch);

  const clearBtn = document.getElementById('clearSearchBtn');
  if (clearBtn && itemSearch) {
    itemSearch.addEventListener('input', () => {
      clearBtn.style.display = itemSearch.value ? 'block' : 'none';
    });
    clearBtn.addEventListener('click', () => {
      itemSearch.value = '';
      clearBtn.style.display = 'none';
      document.getElementById('searchResults').style.display = 'none';
      highlightedIndex = -1;
    });
  }

  document.addEventListener('click', (e) => {
    if (!e.target.closest('#itemSearch') && !e.target.closest('#searchResults')) {
      document.getElementById('searchResults').style.display = 'none';
      highlightedIndex = -1;
    }
  });

  if (itemSearch) {
    itemSearch.addEventListener('keydown', (e) => {
      const results = document.getElementById('searchResults');
      const items = Array.from(results.querySelectorAll('.list-group-item'));
      if (e.key === 'ArrowDown') {
        e.preventDefault();
        highlightedIndex = Math.min(highlightedIndex + 1, items.length - 1);
        highlightItem(items);
      } else if (e.key === 'ArrowUp') {
        e.preventDefault();
        highlightedIndex = Math.max(highlightedIndex - 1, -1);
        highlightItem(items);
      } else if (e.key === 'Enter' && highlightedIndex >= 0) {
        e.preventDefault();
        items[highlightedIndex].click();
      } else if (e.key === 'Escape') {
        results.style.display = 'none';
        highlightedIndex = -1;
      }
    });
  }

  const paymentMethodEl = document.getElementById('paymentMethod');
  if (paymentMethodEl) {
    paymentMethodEl.addEventListener('change', updatePaymentUI);
  }

  const amountReceivedEl = document.getElementById('amountReceived');
  if (amountReceivedEl) {
    amountReceivedEl.addEventListener('input', calculateChange);
  }

  const completeSaleBtn = document.getElementById('completeSale');
  if (completeSaleBtn) {
    completeSaleBtn.addEventListener('click', completeSale);
  }

  const printInvoiceBtn = document.getElementById('printInvoice');
  if (printInvoiceBtn) {
    printInvoiceBtn.addEventListener('click', printInvoice);
  }

  const downloadInvoiceBtn = document.getElementById('downloadInvoice');
  if (downloadInvoiceBtn) {
    downloadInvoiceBtn.addEventListener('click', downloadInvoicePDF);
  }

  const addTaxBtn = document.getElementById('addTaxBtn');
  if (addTaxBtn) {
    addTaxBtn.addEventListener('click', () => {
      appliedTaxes.push({ type: 'GST', rate: 5, manuallyEdited: false });
      renderTaxBuilder();
      renderCart();
    });
  }

  renderTaxBuilder();
  renderCart();
  loadItems();
});

function highlightItem(items) {
  items.forEach((item, index) => {
    item.classList.toggle('bg-primary', index === highlightedIndex);
    item.classList.toggle('text-white', index === highlightedIndex);
    item.classList.toggle('bg-light', index !== highlightedIndex);
  });
}

function handleSearchInput(e) {
  const term = e.target.value.toLowerCase().trim();
  const results = document.getElementById('searchResults');
  if (!term) {
    results.style.display = 'none';
    highlightedIndex = -1;
    return;
  }

  const matches = allItems.filter(item => 
    item.itemName.toLowerCase().includes(term) || 
    (item.itemCode && item.itemCode.toLowerCase().includes(term))
  );

  if (matches.length === 0) {
    results.innerHTML = `<li class="list-group-item empty-state">No items found matching "${term}"</li>`;
    results.style.display = 'block';
    highlightedIndex = -1;
    return;
  }

  results.innerHTML = matches.map((item, index) => 
    `<li class="list-group-item d-flex justify-content-between align-items-center"
        data-item-id="${item.itemId}"
        data-item-name="${item.itemName}"
        data-item-mrp="${item.mrp}"
        data-item-selling="${item.sellingPrice}"
        tabindex="0">
      <span>${item.itemName} (${item.itemCode || 'No Code'})</span>
      <span class="badge bg-primary">MRP ₹${item.mrp.toFixed(2)}</span>
    </li>`
  ).join('');

  results.querySelectorAll('.list-group-item').forEach(item => {
    item.addEventListener('click', () => {
      const itemId = parseInt(item.dataset.itemId);
      const itemName = item.dataset.itemName;
      const mrp = parseFloat(item.dataset.itemMrp);
      const sellingPrice = parseFloat(item.dataset.itemSelling);
      let defaultDiscount = 0;
      if (mrp > 0 && sellingPrice < mrp) {
        defaultDiscount = Math.floor(((mrp - sellingPrice) / mrp) * 100);
      }
      addItemToCart(itemId, itemName, mrp, defaultDiscount);
    });
  });

  results.style.display = 'block';
  highlightedIndex = -1;
}

function addItemToCart(itemId, itemName, mrp, discountPercent = 0) {
  if (mrp <= 0) {
    alert(`Invalid MRP for ${itemName}.`);
    return;
  }
  const existing = cart.find(i => i.itemId === itemId);
  if (existing) {
    existing.quantity += 1;
  } else {
    cart.push({ 
      itemId, 
      itemName, 
      mrp,
      discountPercent: Math.min(100, Math.max(0, discountPercent)),
      quantity: 1
    });
  }
  document.getElementById('itemSearch').value = '';
  document.getElementById('searchResults').style.display = 'none';
  highlightedIndex = -1;
  renderCart();
}

function removeItem(itemId) {
  cart = cart.filter(i => i.itemId !== itemId);
  renderCart();
}

function updateQuantity(itemId, qty) {
  const item = cart.find(i => i.itemId === itemId);
  if (item && qty > 0) item.quantity = parseInt(qty);
  renderCart();
}

function updateItemDiscount(itemId, discount) {
  const item = cart.find(i => i.itemId === itemId);
  if (item) {
    item.discountPercent = Math.min(100, Math.max(0, parseFloat(discount) || 0));
    renderCart();
  }
}

function renderTaxBuilder() {
  const container = document.getElementById('taxBuilder');
  if (!container) return;
  container.innerHTML = '';

  appliedTaxes.forEach((tax, index) => {
    const taxDiv = document.createElement('div');
    taxDiv.className = 'input-group input-group-sm mb-1';
    taxDiv.innerHTML = `
      <select class="form-select tax-type" data-index="${index}">
        <option value="NONE" ${tax.type === 'NONE' ? 'selected' : ''}>No Tax</option>
        <option value="GST" ${tax.type === 'GST' ? 'selected' : ''}>GST</option>
        <option value="ET" ${tax.type === 'ET' ? 'selected' : ''}>Excise Tax</option>
        <option value="CDA" ${tax.type === 'CDA' ? 'selected' : ''}>CDA</option>
        <option value="VAT" ${tax.type === 'VAT' ? 'selected' : ''}>VAT</option>
        <option value="OTHER" ${tax.type === 'OTHER' ? 'selected' : ''}>Other</option>
      </select>
      <input type="number" class="form-control tax-rate" data-index="${index}" 
             value="${tax.rate}" min="0" max="100" step="0.1" placeholder="Rate %">
      <button class="btn btn-outline-danger btn-remove-tax" type="button" data-index="${index}">×</button>
    `;
    container.appendChild(taxDiv);
  });

  container.querySelectorAll('.tax-type').forEach(el => {
    el.addEventListener('change', updateTaxFromUI);
  });
  container.querySelectorAll('.tax-rate').forEach(el => {
    el.addEventListener('input', updateTaxFromUI);
  });
  container.querySelectorAll('.btn-remove-tax').forEach(btn => {
    btn.addEventListener('click', (e) => {
      const index = parseInt(e.target.dataset.index);
      if (index >= 0 && index < appliedTaxes.length) {
        appliedTaxes.splice(index, 1);
        renderTaxBuilder();
        renderCart();
      }
    });
  });
}

function updateTaxFromUI(e) {
  const index = parseInt(e.target.dataset.index);
  if (isNaN(index) || index < 0 || index >= appliedTaxes.length) return;

  if (e.target.classList.contains('tax-type')) {
    const newType = e.target.value;
    appliedTaxes[index].type = newType;
    let defaultRate = 0;
    switch (newType) {
      case 'GST': defaultRate = 5; break;
      case 'ET': defaultRate = 30; break;
      case 'CDA': defaultRate = 0; break;
      case 'VAT': defaultRate = 13; break;
      case 'OTHER': defaultRate = 0; break;
      default: defaultRate = 0;
    }
    if (!appliedTaxes[index].manuallyEdited) {
      appliedTaxes[index].rate = defaultRate;
    }
    const rateInput = document.querySelector(`.tax-rate[data-index="${index}"]`);
    if (rateInput) rateInput.value = appliedTaxes[index].rate;
  } else if (e.target.classList.contains('tax-rate')) {
    appliedTaxes[index].rate = parseFloat(e.target.value) || 0;
    appliedTaxes[index].manuallyEdited = true;
  }
  renderCart();
}

function renderCart() {
  const tbody = document.getElementById('cartItems');
  let subtotal = 0;
  let totalDiscount = 0;

  tbody.innerHTML = cart.map(item => {
    const originalLineTotal = item.mrp * item.quantity;
    const discountAmount = (originalLineTotal * item.discountPercent) / 100;
    const finalLineTotal = originalLineTotal - discountAmount;
    subtotal += originalLineTotal;
    totalDiscount += discountAmount;

    return `
      <tr>
        <td>${item.itemName}</td>
        <td><input type="number" min="1" value="${item.quantity}" onchange="updateQuantity(${item.itemId}, this.value)" style="width:60px;"></td>
        <td>₹${item.mrp.toFixed(2)}</td>
        <td><input type="number" min="0" max="100" step="0.1" value="${item.discountPercent || 0}" onchange="updateItemDiscount(${item.itemId}, this.value)" style="width:60px;" placeholder="%"></td>
        <td>₹${finalLineTotal.toFixed(2)}</td>
        <td><button class="btn btn-sm btn-danger" onclick="removeItem(${item.itemId})">✕</button></td>
      </tr>
    `;
  }).join('');

  let taxableAmount = subtotal - totalDiscount;
  let totalTax = 0;
  appliedTaxes.forEach(tax => {
    if (tax.type !== 'NONE') {
      totalTax += (taxableAmount * tax.rate) / 100;
    }
  });

  const total = taxableAmount + totalTax;
  document.getElementById('subtotalAmount').textContent = `₹${subtotal.toFixed(2)}`;
  document.getElementById('discountAmount').textContent = `₹${totalDiscount.toFixed(2)}`;
  document.getElementById('taxAmount').textContent = `₹${totalTax.toFixed(2)}`;
  document.getElementById('totalAmount').textContent = `₹${total.toFixed(2)}`;

  if (document.getElementById('paymentMethod')?.value === 'CASH') {
    calculateChange();
  }
}

function updatePaymentUI() {
  const paymentMethod = document.getElementById('paymentMethod')?.value;
  const cashSection = document.getElementById('cashChangeSection');
  const printBtn = document.getElementById('printInvoice');

  if (paymentMethod === 'CASH') {
    cashSection?.classList.remove('d-none');
    cashSection.style.display = 'block';
    setTimeout(() => {
      const amountReceived = document.getElementById('amountReceived');
      if (amountReceived) {
        amountReceived.focus();
        amountReceived.select();
      }
    }, 100);
  } else {
    cashSection?.classList.add('d-none');
    cashSection.style.display = 'none';
    ['amountReceived', 'changeAmount'].forEach(id => {
      const el = document.getElementById(id);
      if (el) el.value = '';
    });
  }
  printBtn?.classList.add('d-none');
}

function calculateChange() {
  const totalText = document.getElementById('totalAmount')?.textContent || '₹0.00';
  const total = parseFloat(totalText.replace('₹', '')) || 0;
  const received = parseFloat(document.getElementById('amountReceived').value) || 0;
  const change = received - total;
  const changeEl = document.getElementById('changeAmount');
  if (changeEl) {
    changeEl.value = change >= 0 ? `₹${change.toFixed(2)}` : 'Insufficient!';
  }
}

async function completeSale() {
  if (cart.length === 0) {
    showError('⚠️ Please add at least one item to the cart.');
    return;
  }

  const paymentMethod = document.getElementById('paymentMethod')?.value;
  if (paymentMethod === 'CASH') {
    const received = parseFloat(document.getElementById('amountReceived')?.value) || 0;
    const total = parseFloat(document.getElementById('totalAmount')?.textContent.replace('₹', '')) || 0;
    if (received < total) {
      showError(`❌ Insufficient amount received! You entered ₹${received.toFixed(2)}, but total is ₹${total.toFixed(2)}.`);
      return;
    }
  }

  const hasMissingPrice = cart.some(item => item.mrp <= 0);
  if (hasMissingPrice) {
    showError("⚠️ Some items have invalid MRP. Please reload items.");
    return;
  }

  // Send ONLY item-level discounts (no order-level discount)
  const request = {
    customerName: document.getElementById('customerName')?.value || null,
    customerPhone: document.getElementById('customerPhone')?.value || null,
    paymentMethod: paymentMethod,
    taxes: appliedTaxes.filter(t => t.type !== 'NONE'),
    items: cart.map(i => ({
      itemId: i.itemId,
      quantity: i.quantity,
      mrp: i.mrp,
      discountPercent: i.discountPercent || 0
    }))
  };

  const btn = document.getElementById('completeSale');
  btn.disabled = true;
  btn.innerHTML = '<span class="spinner-border spinner-border-sm me-2" role="status"></span> Processing...';

  try {
    const res = await fetch('http://localhost:8080/api/orders/pos/sale', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify(request)
    });

    if (res.ok) {
      const order = await res.json();
      currentOrder = order;
      showSuccess(`✅ Sale completed! Order #${order.orderId}`);
      document.getElementById('printInvoice')?.classList.remove('d-none');
      generateInvoicePreview(order);

      // Reset form
      cart = [];
      appliedTaxes = [{ type: 'GST', rate: 5, manuallyEdited: false }];
      renderTaxBuilder();
      renderCart();
      ['customerName', 'customerPhone', 'amountReceived', 'changeAmount'].forEach(id => {
        const el = document.getElementById(id);
        if (el) el.value = '';
      });
      document.getElementById('cashChangeSection')?.classList.add('d-none');
    } else {
      const errorText = await res.text();
      showError(`Sale failed: ${errorText}`);
    }
  } catch (e) {
    showError(`Network error: ${e.message}`);
    console.error(e);
  } finally {
    btn.disabled = false;
    btn.innerHTML = '✅ Complete Sale';
  }
}

function generateInvoicePreview(order) {
  let subtotal = 0;
  let totalDiscount = 0;
  cart.forEach(item => {
    const original = item.mrp * item.quantity;
    const disc = (original * item.discountPercent) / 100;
    subtotal += original;
    totalDiscount += disc;
  });

  let taxableAmount = subtotal - totalDiscount;
  let totalTax = 0;
  appliedTaxes.forEach(tax => {
    if (tax.type !== 'NONE') {
      totalTax += (taxableAmount * tax.rate) / 100;
    }
  });
  const total = taxableAmount + totalTax;

  let itemsHtml = '';
  cart.forEach(item => {
    const originalLineTotal = item.mrp * item.quantity;
    const discountAmount = (originalLineTotal * item.discountPercent) / 100;
    const finalLineTotal = originalLineTotal - discountAmount;
    itemsHtml += `<tr><td>${item.itemName}</td><td>${item.quantity}</td><td>₹${item.mrp.toFixed(2)}</td><td>₹${finalLineTotal.toFixed(2)}</td></tr>`;
  });

  document.getElementById('invoicePreview').innerHTML = `
    <div class="invoice-header">
      <h4>DP Inventory System</h4>
      <p><strong>INVOICE</strong></p>
      <p><strong>Date:</strong> ${new Date().toLocaleString()}</p>
      ${order.customerName ? `<p><strong>Customer:</strong> ${order.customerName}</p>` : ''}
      ${order.customerPhone ? `<p><strong>Phone:</strong> ${order.customerPhone}</p>` : ''}
    </div>
    <table class="table table-borderless mb-2">
      <thead><tr><th>Item</th><th>Qty</th><th>MRP</th><th>Total</th></tr></thead>
      <tbody>${itemsHtml}</tbody>
    </table>
    <table class="table table-borderless mb-0">
      <tbody>
        <tr class="summary-row"><td colspan="3"><strong>Subtotal:</strong></td><td><strong>₹${subtotal.toFixed(2)}</strong></td></tr>
        <tr class="summary-row"><td colspan="3"><strong>Discount (-):</strong></td><td><strong>- ₹${totalDiscount.toFixed(2)}</strong></td></tr>
        <tr class="summary-row"><td colspan="3">GST @ 5%:</td><td>+ ₹${totalTax.toFixed(2)}</td></tr>
        <tr class="summary-row"><td colspan="3"><strong>Grand Total:</strong></td><td><strong>₹${total.toFixed(2)}</strong></td></tr>
      </tbody>
    </table>
    <div class="footer-note">
      Payment Method: ${order.paymentMethod}<br>
      Thank you for shopping with us!
    </div>
  `;

  new bootstrap.Modal(document.getElementById('invoiceModal')).show();
}

function printInvoice() {
  window.print();
}

function downloadInvoicePDF() {
  const element = document.getElementById('invoicePreview');
  if (!element) {
    alert("❌ Invoice preview not loaded.");
    return;
  }

  const originalDisplay = {};
  const hideElements = element.querySelectorAll('.btn, .modal-footer, #printInvoice, #downloadInvoice');
  hideElements.forEach(el => {
    originalDisplay[el] = el.style.display;
    el.style.display = 'none';
  });

  html2canvas(element, {
    backgroundColor: null,
    scale: 2,
    useCORS: true
  }).then(canvas => {
    hideElements.forEach(el => {
      el.style.display = originalDisplay[el];
    });

    const imgData = canvas.toDataURL('image/png');
    const { jsPDF } = window.jspdf;
    const pdf = new jsPDF('p', 'mm', 'a4');
    const imgProps = pdf.getImageProperties(imgData);
    const pdfWidth = pdf.internal.pageSize.getWidth();
    const pdfHeight = (imgProps.height * pdfWidth) / imgProps.width;
    pdf.addImage(imgData, 'PNG', 0, 0, pdfWidth, pdfHeight);
    pdf.save(`DP_Invoice_${currentOrder?.orderId || 'POS'}.pdf`);
  }).catch(err => {
    console.error("PDF generation failed:", err);
    alert("❌ Failed to generate PDF. Try printing instead.");
  });
}