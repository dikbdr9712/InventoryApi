// edit-item.js

document.addEventListener('DOMContentLoaded', () => {
  const editForm = document.getElementById('editItemForm');
  const messageDiv = document.getElementById('message');
  const currentImage = document.getElementById('currentImage');
  
  // Pricing method elements
  const pricingMethod = document.getElementById('pricingMethod');
  const pricingInput = document.getElementById('pricingInput');
  const pricingHint = document.getElementById('pricingHint');
  const costPriceHidden = document.getElementById('costPrice');

  // Get itemId from URL (e.g., edit-item.html?itemId=26)
  const urlParams = new URLSearchParams(window.location.search);
  const itemId = urlParams.get('itemId');

  if (!itemId) {
    messageDiv.textContent = '❌ Item ID missing.';
    messageDiv.className = 'error';
    messageDiv.style.display = 'block';
    return;
  }

  // ─────────────────────────────────────────────────────
  // 🔁 Pricing UI Toggle Logic
  // ─────────────────────────────────────────────────────
  function updatePricingUI() {
    if (!pricingMethod || !pricingInput || !pricingHint) return;
    
    if (pricingMethod.value === 'markupPercent') {
      pricingHint.textContent = 'Enter markup % (e.g., 25 for 25%)';
      pricingInput.placeholder = 'e.g., 25.0';
      pricingInput.step = '0.1';
    } else {
      pricingHint.textContent = 'Enter final selling price (e.g., 50.00)';
      pricingInput.placeholder = 'e.g., 50.00';
      pricingInput.step = '0.01';
    }
  }

  if (pricingMethod) {
    pricingMethod.addEventListener('change', updatePricingUI);
  }

  // ─────────────────────────────────────────────────────
  // 🧮 Calculate Selling Price (with round-down preference)
  // ─────────────────────────────────────────────────────
  function calculateSellingPrice(costPrice) {
    const method = pricingMethod?.value || 'sellingPrice';
    const inputVal = parseFloat(pricingInput?.value || '0');

    if (isNaN(inputVal)) {
      throw new Error(`Please enter a valid ${method === 'markupPercent' ? 'Markup %' : 'Selling Price'}.`);
    }

    if (method === 'markupPercent') {
      // ✅ CRITICAL: Validate cost price exists and is > 0
      if (!costPrice || costPrice <= 0) {
        throw new Error("⚠️ Cost Price must be greater than 0 to use Markup %. Please set cost price first or use 'Enter Selling Price' mode.");
      }
      if (inputVal < 0) throw new Error("Markup % cannot be negative.");
      
      const rawPrice = costPrice * (1 + inputVal / 100);
      // 🔽 Round DOWN to 2 decimals (per your preference)
      return Math.floor(rawPrice * 100) / 100;
    } else {
      if (inputVal <= 0) throw new Error("Selling Price must be greater than 0.");
      // 🔽 Round DOWN to 2 decimals for consistency
      return Math.floor(inputVal * 100) / 100;
    }
  }

  // ─────────────────────────────────────────────────────
  // 📥 Load Item Data from API
  // ─────────────────────────────────────────────────────
  async function loadItem(id) {
    try {
      const response = await fetch(`http://localhost:8080/api/items/${id}`);
      if (!response.ok) throw new Error('Failed to fetch item');

      const item = await response.json();
      console.log('✅ Loaded item:', item); // 🔍 Debug log

      // Fill basic form fields
      document.getElementById('itemId').value = item.itemId || item.id;
      document.getElementById('itemName').value = item.itemName || item.name || '';
      document.getElementById('description').value = item.description || '';
      document.getElementById('uom').value = item.uom || 'pcs';
      document.getElementById('barcode').value = item.barcode || '';
      document.getElementById('supplierItemCode').value = item.supplierItemCode || '';
      
      // 💰 CRITICAL: Load cost price into hidden field for markup calculations
      const loadedCostPrice = parseFloat(item.costPrice) || 0;
      if (costPriceHidden) {
        costPriceHidden.value = loadedCostPrice;
        console.log('💰 Cost Price loaded:', loadedCostPrice);
      }

      // 🎯 Handle pricing display:
      // Since backend stores final sellingPrice (not markup%), default to "sellingPrice" mode
      // Markup % mode is primarily for ADDING new items
      if (pricingMethod) {
        pricingMethod.value = 'sellingPrice';
        
        // Optional: Disable markup option in edit mode to avoid confusion
        // Uncomment below if you want to prevent markup mode in edit:
        /*
        const markupOption = pricingMethod.querySelector('option[value="markupPercent"]');
        if (markupOption) {
          markupOption.disabled = true;
          markupOption.textContent = 'Use Markup % (Add Item Only)';
        }
        */
      }
      
      // Load existing selling price into input
      if (pricingInput && item.sellingPrice != null) {
        pricingInput.value = parseFloat(item.sellingPrice).toFixed(2);
        console.log('💵 Selling Price loaded:', item.sellingPrice);
      }
      
      if (pricingHint) {
        pricingHint.textContent = 'Enter final selling price (e.g., 50.00)';
      }

      // Load quantity fields
      const quantityInput = document.getElementById('quantity');
      const thresholdInput = document.getElementById('lowStockThreshold');
      if (quantityInput) quantityInput.value = item.quantity ?? 0;
      if (thresholdInput) thresholdInput.value = item.lowStockThreshold ?? 10;

      // Load image
      if (item.imagePath || item.imageUrl) {
        const imgUrl = item.imagePath?.startsWith('http') 
          ? item.imagePath 
          : `http://localhost:8080${item.imagePath}`;
        currentImage.src = imgUrl;
        console.log('🖼️ Image loaded:', imgUrl);
      } else {
        currentImage.src = '../Images/default.jpg';
      }

      // Initialize UI state
      updatePricingUI();
      
      // Show warning if cost price is missing/zero
      if (loadedCostPrice <= 0) {
        console.warn('⚠️ Cost Price is 0 - Markup % calculations will not work');
        // Optional: Show visual warning in UI
        const pricingRow = pricingMethod?.closest('.form-row') || pricingMethod?.closest('.form-group');
        if (pricingRow && !document.getElementById('costPriceWarning')) {
          const warning = document.createElement('small');
          warning.id = 'costPriceWarning';
          warning.className = 'form-text text-warning mt-1';
          warning.textContent = '⚠️ Cost Price is not set. Markup % requires a valid cost price.';
          pricingRow.parentNode.insertBefore(warning, pricingRow.nextSibling);
        }
      }

    } catch (err) {
      console.error('❌ Load error:', err);
      messageDiv.textContent = '❌ Failed to load item: ' + err.message;
      messageDiv.className = 'error';
      messageDiv.style.display = 'block';
    }
  }

  // Load item on page init
  loadItem(itemId);

  // ─────────────────────────────────────────────────────
  // 📤 Form Submission Handler
  // ─────────────────────────────────────────────────────
  editForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    messageDiv.textContent = '';
    messageDiv.className = '';
    messageDiv.style.display = 'none';

    // Validate item name
    const itemName = document.getElementById('itemName').value.trim();
    if (!itemName) {
      messageDiv.textContent = '❌ Item Name is required.';
      messageDiv.className = 'error';
      messageDiv.style.display = 'block';
      return;
    }

    // ✅ CREATE formData HERE (before any append calls)
    const formData = new FormData();

    // Get cost price for markup calculation
    const costPrice = parseFloat(costPriceHidden?.value || '0');
    
    // Calculate final selling price using shared logic
    let sellingPrice;
    try {
      sellingPrice = calculateSellingPrice(costPrice);
      console.log('🧮 Calculated sellingPrice:', sellingPrice, '(method:', pricingMethod?.value + ')');
    } catch (err) {
      messageDiv.textContent = '❌ ' + err.message;
      messageDiv.className = 'error';
      messageDiv.style.display = 'block';
      return;
    }
    const mrpInput = document.getElementById('mrp')?.value.trim();
	const mrp = mrpInput ? parseFloat(mrpInput) : sellingPrice;
	formData.append('mrp', mrp.toFixed(2));

    // ✅ Append all fields to formData
    formData.append('itemId', document.getElementById('itemId').value);
    formData.append('itemName', itemName);
    formData.append('description', document.getElementById('description').value.trim() || '');
    formData.append('uom', document.getElementById('uom').value || 'pcs');
    
    // ✅ Send calculated sellingPrice (backend expects final price)
    formData.append('sellingPrice', sellingPrice.toFixed(2));
    
    // Optional: send costPrice if your backend validates/updates it
    if (costPrice > 0) {
      formData.append('costPrice', costPrice.toFixed(2));
    }
    
    formData.append('barcode', document.getElementById('barcode').value.trim() || '');
    formData.append('supplierItemCode', document.getElementById('supplierItemCode').value.trim() || '');

    // Quantity fields
    const quantityStr = document.getElementById('quantity')?.value.trim();
    const quantity = parseInt(quantityStr) || 0;
    formData.append('quantity', quantity);

    const lowStockThreshold = parseInt(document.getElementById('lowStockThreshold')?.value) || 10;
    formData.append('lowStockThreshold', lowStockThreshold);

    // Image file (if selected)
    const imageFile = document.getElementById('imageFile')?.files?.[0];
    if (imageFile) {
      formData.append('image', imageFile); // Match backend @RequestParam("image")
      console.log('📎 Image file attached:', imageFile.name);
    }

    // Send to backend
    try {
      console.log('📤 Sending PUT request to /api/items/' + itemId);
      const response = await fetch(`http://localhost:8080/api/items/${itemId}`, {
        method: 'PUT',
        body: formData
        // ⚠️ DO NOT set Content-Type header - browser sets multipart boundary automatically
      });

      if (response.ok) {
        console.log('✅ Item updated successfully');
        messageDiv.textContent = '✅ Item updated successfully!';
        messageDiv.className = 'success';
        messageDiv.style.display = 'block';
        
        // Redirect after success
        setTimeout(() => {
          window.location.href = 'Product.html';
        }, 1500);
      } else {
        const errorText = await response.text();
        console.error('❌ Server error:', errorText);
        messageDiv.textContent = '❌ Failed: ' + (errorText || 'Unknown error');
        messageDiv.className = 'error';
        messageDiv.style.display = 'block';
      }
    } catch (err) {
      console.error('❌ Network error:', err);
      messageDiv.textContent = '❌ Network error: ' + err.message;
      messageDiv.className = 'error';
      messageDiv.style.display = 'block';
    }
  });
});