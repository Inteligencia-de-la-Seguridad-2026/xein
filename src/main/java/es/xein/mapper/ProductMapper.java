package es.xein.mapper;

import es.xein.Model.Product;
import es.xein.Model.Review;
import es.xein.dto.ProductDTO;
import es.xein.dto.ReviewDTO;

import java.util.List;
import java.util.Collection;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProductMapper {
    
    @Mapping(target = "quantity", ignore = true)
    @Mapping(target = "rating", source = "rating")
    ProductDTO toDTO(Product product);
    
    List<ProductDTO> toDTOs(Collection<Product> products);

    @Mapping(target = "imageData", ignore = true)
    @Mapping(target = "amount", ignore = true)
    @Mapping(target = "reviews", ignore = true)
    @Mapping(target = "imagePath", ignore = true)
    @Mapping(target = "rating", ignore = true)
    @Mapping(target = "returnPolicyData", ignore = true)
    @Mapping(target = "returnPolicyPath", ignore = true)
    Product toDomain(ProductDTO productDTO);

    @Mapping(target = "product", ignore = true)
    @Mapping(target = "user", ignore = true)
    Review toDomain(ReviewDTO reviewDTO);
}