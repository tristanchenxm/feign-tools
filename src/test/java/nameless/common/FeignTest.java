package nameless.common;

import nameless.common.feignclient.EchoServiceFeignClient;
import org.junit.Assert;
import org.junit.Before;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

//@RunWith(SpringRunner.class)
@SpringBootTest(classes = TestApplication.class)
public class FeignTest {
    @Autowired
    private EchoServiceFeignClient echoServiceFeignClient;


    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void testGetEcho() throws InterruptedException {
        echoServiceFeignClient.getEcho(1, "hello");
        Assert.assertTrue(true);
    }
}
