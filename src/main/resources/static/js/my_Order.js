function updateQuantity(productId, change) {
    const input = document.getElementById(`counter_${productId}`);
    let value = parseInt(input.value) + change;
    if (value < 1) value = 1;
    input.value = value;

    // Get the form and submit it
    const form = input.closest('.quantity-form');
    if (form) {
        form.submit();
    }
}

function updateOrderSummary(data) {
    const orderSummary = document.querySelector('.bg-white ul');
    const totalElement = document.querySelector('.order-total');
    
    // Check that the elements exist
    if (!orderSummary || !totalElement || !data.products) {
        console.error("No elements found to update the summary");
        return;
    }

    try {
        // Clear current summary
        orderSummary.innerHTML = '';

        // Add updated products
        data.products.forEach(product => {
            const li = document.createElement('li');
            li.className = 'flex justify-between items-center pb-2 border-b border-[#CBD3E6]/50';
            li.innerHTML = `
                <span class="text-[#567C8D]">${product.name} <span class="text-[#CBD3E6]">x${product.amount}</span></span>
                <span class="font-medium text-[#294156]">$${product.price}</span>
            `;
            orderSummary.appendChild(li);
        });

        // Update order total
        totalElement.innerHTML = `
            <div class="flex justify-between items-center">
                <span class="text-[#567C8D]"><i class="fas fa-money-bill-wave mr-2"></i>Total</span>
                <span class="font-bold text-xl text-[#294156]">$${data.total}</span>
            </div>
        `;
        
        // Also update the quantities in the inputs
        data.products.forEach(product => {
            const input = document.getElementById(`counter_${product.id}`);
            if (input) {
                input.value = product.amount;
            }
        });
    } catch (error) {
        console.error("Error updating the summary:", error);
    }
}

function removeFromCart(productId) {
    const formData = new FormData();
    formData.append('productId', productId);
    
    fetch('/remove-from-order', {
        method: 'POST',
        body: formData
    }).then(response => {
        if (response.ok) {
            window.location.reload();
        }
    });
}

function deleteOrder() {
    fetch('/delete-order', {
        method: 'POST',
    }).then(response => {
        if (response.ok) {
            window.location.reload();
        }
    });
}

// Add event listeners to all quantity inputs to update them when changing
document.addEventListener('DOMContentLoaded', function() {
    const quantityInputs = document.querySelectorAll('.quantity-input');
    
    quantityInputs.forEach(input => {
        // Original value to restore in case of error
        let originalValue = input.value;
        
        // Event for when the user finishes editing the value
        input.addEventListener('change', function() {
            const value = parseInt(this.value);
            
            if (isNaN(value) || value < 1) {
                this.value = originalValue;
                return;
            }
            
            // Submit the form
            const form = this.closest('.quantity-form');
            if (form) {
                form.submit();
            }
            
            originalValue = this.value;
        });
        
        // Prevent negative values
        input.addEventListener('input', function() {
            if (this.value < 1 && this.value !== '') {
                this.value = 1;
            }
        });
    });
});

 function formatCardNum(input) {
            let val = input.value.replace(/\D/g, '');
            val = val.replace(/(.{4})/g, '$1 ').trim();
            input.value = val.substring(0, 19);
        }

        function formatExpiry(input) {
            let val = input.value.replace(/\D/g, '');
            if (val.length >= 2) val = val.substring(0, 2) + '/' + val.substring(2, 4);
            input.value = val.substring(0, 5);
        }

        document.getElementById('confirmOrderForm')?.addEventListener('submit', async function(e) {
            e.preventDefault();
            
            const form = this;
            const submitBtn = document.getElementById('confirmOrderBtn');
            const alertBox = document.getElementById('paymentAlert');
            const refInput = document.getElementById('paymentReferenceInput');
            
            const rawTotal = parseFloat("{{order.total}}" || "0");
            const amountCents = Math.round(rawTotal * 100);
            const referenceId = "{{order.referenceId}}" || "REF_" + Date.now();
            const payerId = document.getElementById('payerIdInput').value.trim();
            const payeeId = "{{store.identifier}}" || "MERCHANT_STORE";

            submitBtn.disabled = true;
            submitBtn.innerHTML = '<i class="fas fa-spinner fa-spin mr-2"></i> Processing Payment...';
            alertBox.className = "hidden";

            try {
                // 1. Petición POST al backend de pagos en Python
                const response = await fetch('/transactions', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        reference: referenceId,
                        payer: payerId,
                        payee: payeeId,
                        amount_cents: amountCents,
                        currency: "LAB"
                    })
                });

                const data = await response.json();

                if (response.ok || response.status === 201) {
                    // 2. Pago aprobado: Adjuntamos la referencia de pago y enviamos el formulario HTML a /confirm-order
                    refInput.value = data.reference;
                    alertBox.className = "p-3 mb-3 rounded-lg text-xs border bg-emerald-50 text-emerald-800 border-emerald-200 flex items-center";
                    alertBox.innerHTML = '<i class="fas fa-check-circle text-emerald-500 mr-2"></i> Payment approved! Finalizing order...';
                    
                    setTimeout(() => {
                        form.submit();
                    }, 1000);
                } else {
                    alertBox.className = "p-3 mb-3 rounded-lg text-xs border bg-rose-50 text-rose-800 border-rose-200 flex items-center";
                    alertBox.innerHTML = `<i class="fas fa-exclamation-circle text-rose-500 mr-2"></i> ${data.error || 'Payment failed.'}`;
                    submitBtn.disabled = false;
                    submitBtn.innerHTML = '<i class="fas fa-check-circle mr-2"></i> Confirm Order & Pay (${{order.total}})';
                }
            } catch (err) {
                alertBox.className = "p-3 mb-3 rounded-lg text-xs border bg-rose-50 text-rose-800 border-rose-200 flex items-center";
                alertBox.innerHTML = '<i class="fas fa-wifi text-rose-500 mr-2"></i> Could not connect to payment server.';
                submitBtn.disabled = false;
                submitBtn.innerHTML = '<i class="fas fa-check-circle mr-2"></i> Confirm Order & Pay (${{order.total}})';
            }
        });