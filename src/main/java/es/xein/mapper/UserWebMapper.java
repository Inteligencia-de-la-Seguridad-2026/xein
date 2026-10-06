package es.xein.mapper;

import es.xein.Model.Order;
import es.xein.Model.Product;
import es.xein.Model.Review;
import es.xein.Model.User;
import es.xein.dto.OrderDTO;
import es.xein.dto.ProductWebDTO;
import es.xein.dto.ReviewDTO;
import es.xein.dto.UserWebDTO;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import java.util.Collection;
import java.util.List;

@Mapper(componentModel = "spring")
public interface UserWebMapper {
    
    @Mapping(target = "orders", ignore = true)
    @Mapping(target = "reviews", ignore = true)
    UserWebDTO toDTO(User user);
    
    List<UserWebDTO> toDTOs(Collection<User> users);
    
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "currentOrder", ignore = true)
    User toDomain(UserWebDTO userWebDTO);

    @Mapping(target = "userOrderNumber", ignore = true)
    Order toDomain(OrderDTO orderDTO);

    @Mapping(target = "amount", ignore = true)
    @Mapping(target = "imagePath", ignore = true)
    @Mapping(target = "imageData", ignore = true)
    @Mapping(target = "reviews", ignore = true)
    @Mapping(target = "rating", ignore = true)
    Product toDomain(ProductWebDTO productWebDTO);


    @Mapping(target = "user", ignore = true)
    @Mapping(target = "product", ignore = true)
    Review toDomain(ReviewDTO reviewDTO);
} 