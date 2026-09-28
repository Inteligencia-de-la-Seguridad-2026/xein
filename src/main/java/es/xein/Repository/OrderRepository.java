package es.xein.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import es.xein.Model.Order;
import es.xein.Model.User;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUser(User user);
    List<Order> findByUserOrderById(User user);
    //List<Order> findByUserOrderByCreatedAtDesc(User user); 
}