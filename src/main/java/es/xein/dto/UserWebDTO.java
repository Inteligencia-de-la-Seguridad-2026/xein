package es.xein.dto;

import java.util.List;
import es.xein.Model.UserRole;
import es.xein.dto.ReviewDTO;
import es.xein.dto.OrderDTO;


public record UserWebDTO(
    Long id,
    String firstName,
    String lastName,
    String email,
    String password,
    String address,
    int age,
    int phoneNumber,
    List<ReviewDTO> reviews,
    List<OrderDTO> orders,
    UserRole role,
    String pdfPath
) {} 
