package edu.sjsu.tutorconnect.dto;
import java.util.List;
public record Page<T>(List<T> items, int page, int size, long total) {
 public int totalPages() { return (int) Math.max(1, (total + size - 1) / size); }
 public boolean hasPrevious() { return page > 0; }
 public boolean hasNext() { return page + 1 < totalPages(); }
}
