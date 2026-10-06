package es.xein.Controller;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import es.xein.Model.UserRole;
import es.xein.Service.OrderService;
import es.xein.Service.ProductService;
import es.xein.Service.UserService;
import es.xein.Service.PaymentClient;
import es.xein.Service.PaymentClient.PaymentException;
import es.xein.dto.OrderDTO;
import es.xein.dto.ProductWebDTO;
import es.xein.dto.UserWebDTO;
import es.xein.Model.Order;
import es.xein.Model.Product;
import es.xein.Model.User;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;


@Controller
@Transactional
public class OrderController {
    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);

    @Autowired
    private ProductService productService;

    @Autowired
    private UserService userService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private PaymentClient paymentClient;
    
    @PersistenceContext
    private EntityManager entityManager;
    
    public Order getCurrentOrder() {
        
        User currentUser = userService.getUserEntity();
        Order order=userService.getCurrentOrder(currentUser);
        
        // If there is no order in the session or the user is null, it returns null
        if (order == null || currentUser == null || currentUser.getId() == null) {
            return null;

        }
        
        // If the order exists but belongs to a different user, create a new one
        if (order.getUser() == null || order.getUser().getId() == null || !order.getUser().getId().equals(currentUser.getId())) {
            String address = currentUser.getAddress();
            order = orderService.createOrder(userService.getUserWebDTO(currentUser), address);
            userService.setCurrentOrder(currentUser, order);
        }
        
        return order;
    }

    public void setCurrentOrder(Order order) {
        userService.setCurrentOrder(userService.getUserEntity(), order);
    }

    public int getCartItemCount() {      
        Order currentOrder = getCurrentOrder();
        if (currentOrder == null || currentOrder.getProducts() == null) {
            return 0;
        }
        return currentOrder.getProducts().size();
    }

    @GetMapping("/view_order_products")
    public String showProducts(Model model) {
        UserWebDTO currentUser=userService.getUser();    
        Order currentOrder = getCurrentOrder();

        if (currentOrder == null || !currentOrder.getUser().getId().equals(currentUser.id())) {
            currentOrder = orderService.createOrder(currentUser, currentUser.address());
        }

        model.addAttribute("products", productService.getAllProductsWeb());
        model.addAttribute("order", currentOrder);
        model.addAttribute("isAdmin", currentUser.role() == UserRole.ADMIN);
        model.addAttribute("cartItemCount", getCartItemCount());
        return "Wellcome";
    }

    // Add product to order
    @PostMapping("/add-to-order")
    @ResponseBody
    public Map<String, Object> addToOrder(@RequestParam Long productId) {
        Map<String, Object> response = new HashMap<>();
        try {
            ProductWebDTO productWebDTO = productService.getProductByIdWeb(productId);
            UserWebDTO currentUser = userService.getUser();
            
            if (currentUser == null) {
                response.put("error", "User not logged in");
                return response;
            }
            
            if (productWebDTO == null) {
                response.put("error", "Product not found");
                return response;
            }

            if (productWebDTO.stock() <= 0) {
                response.put("error", "We're sorry, we do not have enough stock at the moment, try later");
                return response;
            }

            Order currentOrder = getCurrentOrder();
            if (currentOrder == null) {
                currentOrder = orderService.createOrder(currentUser, currentUser.address());
                setCurrentOrder(currentOrder);
            }

            int currentQuantity = getProductQuantity(currentOrder, productId);
            if (currentQuantity + 1 > productWebDTO.stock()) {
                response.put("error", "We're sorry, we do not have enough stock at the moment, try later");
                return response;
            }

            currentOrder = orderService.addProductToOrder(productWebDTO, 1, currentUser, currentOrder);
            currentOrder = orderService.calculateTotal(currentOrder);
            setCurrentOrder(currentOrder);
            
            response.put("success", true);
            response.put("cartSize", getCartItemCount());
            return response;
        } catch (Exception e) {
            response.put("error", e.getMessage());
            return response;
        }
    }

    // View current order
    @GetMapping("/view-order")
    public String viewOrder(Model model) {
        UserWebDTO currentUser = userService.getUser();
        
        // Check if user is logged in, redirect to cart prompt if not
        if (currentUser == null) {
            return "redirect:/cart-prompt";
        }
        
        Order currentOrder = getCurrentOrder();
        model.addAttribute("isAdmin", currentUser.role() == UserRole.ADMIN);
        model.addAttribute("cartItemCount", getCartItemCount());
        
        if (currentOrder == null || currentOrder.getProducts() == null || currentOrder.getProducts().isEmpty()) {
            model.addAttribute("message", "Your cart is empty");
            return "my_Order";
        }
        
        model.addAttribute("order", currentOrder);
        model.addAttribute("products", currentOrder.getProducts());
        model.addAttribute("quantities", currentOrder.getQuantities());
        model.addAttribute("total", currentOrder.getTotal());
        model.addAttribute("user", currentUser);
        return "my_Order";
    }

    // Remove a product from the order
    @PostMapping("/remove-from-order")
    public String removeFromOrder(@RequestParam Long productId, Model model) {
        UserWebDTO currentUser = userService.getUser();
        
        if (currentUser == null) {
            return "redirect:/cart-prompt";
        }
        
        Order currentOrder = getCurrentOrder();
        if (currentOrder != null) {
            ProductWebDTO productWebDTO = productService.getProductByIdWeb(productId);
            if (productWebDTO != null) {
                currentOrder = orderService.removeProductFromOrder(currentOrder, productWebDTO);
                setCurrentOrder(currentOrder);
            }
        }
        setCurrentOrder(currentOrder);
        model.addAttribute("order", currentOrder);
        model.addAttribute("cartItemCount", getCartItemCount());
        return "redirect:/view-order";
    }

    //Delete cart order
    @GetMapping("/delete-order")
    public String deleteOrder(Model model) {
        UserWebDTO currentUser = userService.getUser();
        if (currentUser == null) {
            return "redirect:/cart-prompt";
        }

        Order currentOrder = getCurrentOrder();
        if (currentOrder == null || currentOrder.getProducts().isEmpty()) {
            model.addAttribute("message", "No active order to delete");
            return "redirect:/products";
        }

        try {
            orderService.delete(currentOrder);
            model.addAttribute("message", "Order deleted successfully");
        } catch (IllegalArgumentException | SecurityException e) {
            model.addAttribute("error", e.getMessage());
        } catch (Exception e) {
            model.addAttribute("error", "Error deleting order: " + e.getMessage());
        }

        model.addAttribute("products", productService.getAllProductsWeb());
        model.addAttribute("isAdmin", currentUser.role() == UserRole.ADMIN);
        model.addAttribute("cartItemCount", getCartItemCount());
        return "Wellcome";
    }
    
    @DeleteMapping("/delete-order/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteOrder(@PathVariable("id") Long orderId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Order orderToDelete = orderService.findById(orderId);
            if (orderToDelete == null) {
                response.put("error", "Order not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
            orderService.delete(orderToDelete);
            response.put("success", true);
            response.put("message", "Order deleted successfully");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        } catch (SecurityException e) {
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        } catch (Exception e) {
            response.put("error", "Error deleting order: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    //Update products' amount
    @PostMapping("/update-amount")
    public String updateAmount(Model model, @RequestParam int amount, @RequestParam Long productId) {
        UserWebDTO currentUser = userService.getUser();
        
        if (currentUser == null) {
            return "redirect:/cart-prompt";
        }
        
        Order currentOrder = getCurrentOrder();
        if (currentOrder == null) {
            model.addAttribute("error", "No active order");
            return "redirect:/products";
        }

        if (amount < 1) {
            model.addAttribute("error", "Quantity must be greater than 0");
            model.addAttribute("order", currentOrder);
            model.addAttribute("total", currentOrder.getTotal());
            return "my_Order";
        }

        ProductWebDTO productWebDTO = productService.getProductByIdWeb(productId);
        if (productWebDTO == null) {
            model.addAttribute("error", "Product not found");
            return "redirect:/products";
        }

        if (amount > productWebDTO.stock()) {
            model.addAttribute("errorMessage", "We're sorry, we do not have enough stock at the moment, try later");
            model.addAttribute("order", currentOrder);
            model.addAttribute("total", currentOrder.getTotal());
            model.addAttribute("isAdmin", currentUser.role() == UserRole.ADMIN);
            return "my_Order";
        }

        currentOrder = orderService.setProductQuantity(currentOrder, productWebDTO, amount);
        currentOrder = orderService.calculateTotal(currentOrder);
        setCurrentOrder(currentOrder);
        
        model.addAttribute("order", currentOrder);
        model.addAttribute("products", currentOrder.getProducts());
        model.addAttribute("quantities", currentOrder.getQuantities());
        model.addAttribute("total", currentOrder.getTotal());
        model.addAttribute("isAdmin", currentUser.role() == UserRole.ADMIN);
        model.addAttribute("cartItemCount", getCartItemCount());
        return "my_Order";
    }

    // Confirm order
    @PostMapping("/confirm-order")
    @Transactional
    public String confirmOrder(Model model, RedirectAttributes redirectAttributes,
                               @RequestParam String address,
                               @RequestParam String cardNumber,
                               @RequestParam String expiry,
                               @RequestParam String cvv) {
        try {
            UserWebDTO currentUser = userService.getUser();
            
            if (currentUser == null) {
                return "redirect:/cart-prompt";
            }
            
            Order currentOrder = getCurrentOrder();
            if (currentOrder == null || currentOrder.getProducts().isEmpty()) {
                model.addAttribute("error", "No products in the order");
                return "redirect:/view-order";
            }

            if (address == null || address.trim().isEmpty()) {
                model.addAttribute("error", "Shipping address is required");
                return "redirect:/view-order";
            }

            // Verify stock before making any changes
            for (Product orderProduct : currentOrder.getProducts()) {
                ProductWebDTO productWebDTO = productService.getProductByIdWeb(orderProduct.getId());
                if (productWebDTO == null) {
                    model.addAttribute("error", "Some products do not have enough stock");
                    return "redirect:/view-order";
                }
                int quantity = getProductQuantity(currentOrder, orderProduct.getId());
                if (quantity > productWebDTO.stock()) {
                    model.addAttribute("error", "Some products do not have enough stock");
                    return "redirect:/view-order";
                }
            }

            currentOrder.calculateTotal();
            String paymentReference = paymentClient.approveCheckout(
                    currentUser.id(), currentOrder.getId(), currentOrder.getTotal(),
                    cardNumber, expiry, cvv);
            logger.info("Simulated checkout approved: reference={}, userId={}, orderId={}",
                    paymentReference, currentUser.id(), currentOrder.getId());

            // Update stock and save changes
            for (Product product : currentOrder.getProducts()) {
                ProductWebDTO productWebDTO = productService.getProductByIdWeb(product.getId());
                int quantity = getProductQuantity(currentOrder, product.getId());
                Product stockProduct = productService.getProductById(product.getId());
                stockProduct.setStock(stockProduct.getStock() - quantity);
                entityManager.merge(stockProduct);
            }

            // Configure and save the order
            currentOrder = orderService.setAddress(currentOrder, address);
            List<Order> userOrders = orderService.getOrdersByUser(currentUser);
            int userOrderNumber = userOrders.size() + 1;
            currentOrder = orderService.setUserOrderNumber(currentOrder, userOrderNumber);
            
            // Save the confirmed order
            Order confirmedOrder = currentOrder;
            orderService.save(confirmedOrder);

            // Create a new empty order
            currentOrder = orderService.createOrder(currentUser, currentUser.address());
            setCurrentOrder(currentOrder);

            model.addAttribute("order", confirmedOrder);
            model.addAttribute("address", address);
            model.addAttribute("userOrderNumber", userOrderNumber);
            model.addAttribute("paymentReference", paymentReference);
            return "confirm_order";
        } catch (PaymentException exception) {
            redirectAttributes.addFlashAttribute("paymentError", exception.getMessage());
            return "redirect:/view-order";
        } catch (Exception e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            logger.error("Order confirmation failed", e);
            redirectAttributes.addFlashAttribute("paymentError", "The order could not be confirmed");
            return "redirect:/view-order";
        }
    }

    public int getProductQuantity(Order order, Long productId) {
        if (order == null || order.getProducts() == null) {
            return 0;
        }
        return order.getQuantities().getOrDefault(productId, 0);
    }
}


