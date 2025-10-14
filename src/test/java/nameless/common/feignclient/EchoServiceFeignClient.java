package nameless.common.feignclient;

import nameless.common.feign.LogInvocation;
import nameless.common.service.EchoService;
import org.springframework.cloud.openfeign.FeignClient;

@LogInvocation
@FeignClient(name = "echoServiceFeignClient", url = "https://postman-echo.com")
public interface EchoServiceFeignClient extends EchoService {
}
