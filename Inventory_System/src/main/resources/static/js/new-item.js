document.addEventListener('DOMContentLoaded', () => {
  const addItemForm = document.getElementById('addItemForm');
  const messageDiv = document.getElementById('message');
  const pricingMethod = document.getElementById('pricingMethod');
  const pricingInput = document.getElementById('pricingInput');
  const pricingHint = document.getElementById('pricingHint');

  // Update UI when pricing method changes
  pricingMethod.addEventListener('change', () => {
    if (pricingMethod.value === 'markupPercent') {
      pricingHint.textContent = 'Enter markup % (e.g., 25 for 25%)';
      pricingInput.placeholder = 'e.g., 25.0';
      pricingInput.step = '0.1';
    } else {
      pricingHint.textContent = 'Enter final selling price (e.g., 50.00)';
      pricingInput.placeholder = 'e.g., 50.00';
      pricingInput.step = '0.01';
    }
  });

  // Calculate final selling price
  function calculateSellingPrice(costPrice) {
    const method = pricingMethod.value;
    const inputVal = parseFloat(pricingInput.value);

    if (isNaN(inputVal)) {
      throw new Error(`Please enter a valid ${method === 'markupPercent' ? 'Markup %' : 'Selling Price'}.`);
    }

    if (method === 'markupPercent') {
      if (inputVal < 0) throw new Error("Markup % cannot be negative.");
      return costPrice * (1 + inputVal / 100);
    } else {
      if (inputVal <= 0) throw new Error("Selling Price must be greater than 0.");
      return inputVal;
    }
  }

  // Form submission
  addItemForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    messageDiv.textContent = '';
    messageDiv.className = '';

    // 1️⃣ Get and validate basic fields
    const itemName = document.getElementById('itemName').value.trim();
    const costPriceStr = document.getElementById('costPrice').value.trim();
    const category = document.getElementById('category').value.trim();

    if (!itemName) {
      messageDiv.textContent = '❌ Item Name is required.';
      messageDiv.className = 'error';
      return;
    }

    const costPrice = parseFloat(costPriceStr);
    if (isNaN(costPrice) || costPrice < 0) {
      messageDiv.textContent = '❌ Valid Cost Price is required (must be ≥ 0).';
      messageDiv.className = 'error';
      return;
    }

    if (!category) {
      messageDiv.textContent = '❌ Category is required.';
      messageDiv.className = 'error';
      return;
    }

    // 2️⃣ Calculate selling price and markupPercent
    let sellingPrice;
    let markupPercent = null;
    
    try {
      if (pricingMethod.value === 'markupPercent') {
        markupPercent = parseFloat(pricingInput.value);
        if (isNaN(markupPercent) || markupPercent < 0) {
          throw new Error("Markup % must be a non-negative number.");
        }
        sellingPrice = calculateSellingPrice(costPrice);
      } else {
        sellingPrice = parseFloat(pricingInput.value);
        if (isNaN(sellingPrice) || sellingPrice <= 0) {
          throw new Error("Selling Price must be greater than 0.");
        }
      }
    } catch (err) {
      messageDiv.textContent = '❌ ' + err.message;
      messageDiv.className = 'error';
      return;
    }

    // 3️⃣ Validate quantity
    const quantityStr = document.getElementById('quantity').value.trim();
    const quantity = parseInt(quantityStr);
    if (isNaN(quantity) || quantity < 0) {
      messageDiv.textContent = '❌ Quantity must be a non-negative number.';
      messageDiv.className = 'error';
      return;
    }

    // 4️⃣ Validate low stock threshold (optional)
    const lowStockThreshold = parseInt(document.getElementById('lowStockThreshold').value) || 10;
    if (lowStockThreshold < 0) {
      messageDiv.textContent = '❌ Low stock threshold cannot be negative.';
      messageDiv.className = 'error';
      return;
    }

    // 5️⃣ ✅ CREATE FormData HERE (before any append calls)
    const formData = new FormData();
    formData.append('itemName', itemName);
    formData.append('description', document.getElementById('description').value.trim() || '');
    formData.append('uom', document.getElementById('uom').value || 'pcs');
    formData.append('costPrice', costPrice.toFixed(2));
    formData.append('sellingPrice', sellingPrice.toFixed(2));
    
    // ✅ Handle MRP for discount calculation
    const mrpInput = document.getElementById('mrp')?.value.trim();
    const mrp = mrpInput ? parseFloat(mrpInput) : sellingPrice; // Default to selling price if empty
    formData.append('mrp', mrp.toFixed(2));
    
    formData.append('quantity', quantity);
    formData.append('category', category);
    formData.append('barcode', document.getElementById('barcode').value.trim() || '');
    formData.append('supplierItemCode', document.getElementById('supplierItemCode').value.trim() || '');
    
    // ✅ Append markupPercent only if pricing method is markup (for backend reference)
    if (markupPercent !== null && !isNaN(markupPercent)) {
      formData.append('markupPercent', markupPercent.toFixed(2));
    }

    // 6️⃣ Handle image file
    const imageFile = document.getElementById('imageFile').files[0];
    if (imageFile) {
      formData.append('images', imageFile);
    }

    // 7️⃣ Submit to backend
    try {
      const response = await fetch('http://localhost:8080/api/items/addItems', {
        method: 'POST',
        body: formData
      });

      if (response.ok) {
        messageDiv.textContent = '✅ Item added successfully!';
        messageDiv.className = 'success';
        addItemForm.reset();
        pricingHint.textContent = 'Enter final selling price (e.g., 50.00)';
      } else {
        const errorText = await response.text();
        messageDiv.textContent = '❌ Failed: ' + (errorText || 'Unknown error');
        messageDiv.className = 'error';
      }
    } catch (err) {
      messageDiv.textContent = '❌ Network error: ' + err.message;
      messageDiv.className = 'error';
    }
  });
});