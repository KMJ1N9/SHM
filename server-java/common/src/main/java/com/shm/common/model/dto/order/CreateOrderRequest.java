package com.shm.common.model.dto.order;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建订单请求（与 Node.js orderService.create 参数一致）
 *
 * <p>前端发送 snake_case JSON（{@code {"product_id": 123}}），
 * JacksonConfig 的 {@code SnakeCaseForBoth} 将 setter 也转换为 snake_case，
 * 因此反序列化自动将 JSON "product_id" 映射到 Java productId。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {

    @NotNull(message = "商品 ID 不能为空")
    @JsonProperty("product_id")
    private Long productId;
}
