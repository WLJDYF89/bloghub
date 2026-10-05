package guat.lxy.pojo.dto;

import lombok.Data;

/**
 * 找回密码请求参数
 * - userName：要找回的账号
 * - password：新密码（≥6 位，服务端校验后用 BCrypt 加密存储）
 */
@Data
public class UserResetPasswordDTO {

    private String userName;

    private String password;
}
