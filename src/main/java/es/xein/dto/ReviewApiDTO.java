package es.xein.dto;

public record ReviewApiDTO (
    Long id,
    String comment,
    int rating,
    String userName,  
    String productName 
){
    
}
