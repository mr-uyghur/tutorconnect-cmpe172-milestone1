package edu.sjsu.tutorconnect.dto;
import java.util.List;
public record HomeDto(String application, List<ProviderDto> providers, List<ServiceDto> services) {}
