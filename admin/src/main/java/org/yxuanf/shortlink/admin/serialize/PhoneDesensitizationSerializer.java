package org.yxuanf.shortlink.admin.serialize;

import cn.hutool.core.util.DesensitizedUtil;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

public class PhoneDesensitizationSerializer extends JsonSerializer<String> {
    /**
     * 电话脱敏
     */
    @Override
    public void serialize(String phone, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        String phoneDesensitization = DesensitizedUtil.mobilePhone(phone);
        // 在序列化时，若有 @JsonSerialize(using = PhoneDesensitizationSerializer.class)注解则进行脱敏
        jsonGenerator.writeString(phoneDesensitization);
    }
}