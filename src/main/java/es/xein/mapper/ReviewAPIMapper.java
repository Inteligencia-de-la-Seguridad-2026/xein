package es.xein.mapper;

import java.util.Collection;
import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import es.xein.Model.Review;
import es.xein.dto.ReviewApiDTO;

@Mapper(componentModel = "spring")
public interface ReviewAPIMapper {

    @Mapping(target = "userName", source = "user.firstName") 
    @Mapping(target = "productName", source = "product.name") 
    ReviewApiDTO toDTO(Review review);

    @Mapping(target = "user", ignore = true)
    @Mapping(target = "product", ignore = true)
    Review toDomain(ReviewApiDTO dto);

    @Mapping(target = "user", ignore = true)
    @Mapping(target = "product", ignore = true)
    List<ReviewApiDTO> toDTOs(Collection<Review> reviews);
}
