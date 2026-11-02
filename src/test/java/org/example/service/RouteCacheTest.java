package org.example.service;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class RouteCacheTest {
    @Test void cacheHasHardCapacityLimit() {
        AmapRouteService routes=spy(new AmapRouteService());
        doReturn(AmapRouteService.RouteResult.ok(BigDecimal.ONE,BigDecimal.ONE,"104,30;104.1,30",null)).when(routes).requestRoute(anyString(),anyString());
        for(int i=0;i<300;i++) routes.fetchRoute("104,30",(104+i/1000.0)+",30");
        assertEquals(128,routes.cachedRouteCount());routes.shutdown();
    }
    @Test void concurrentSameRouteMakesOnlyOneExternalRequest() throws Exception {
        AmapRouteService routes=spy(new AmapRouteService());
        doAnswer(call->{Thread.sleep(20);return AmapRouteService.RouteResult.ok(BigDecimal.ONE,BigDecimal.ONE,"104,30;104.1,30",null);}).when(routes).requestRoute(anyString(),anyString());
        ExecutorService workers=Executors.newFixedThreadPool(16);
        try {
            var futures=new java.util.ArrayList<Future<?>>();
            for(int i=0;i<32;i++) futures.add(workers.submit(()->routes.fetchRoute("104,30","104.1,30")));
            for(var future:futures) future.get(5,TimeUnit.SECONDS);
            verify(routes,times(1)).requestRoute("104,30","104.1,30");
        } finally { workers.shutdownNow();routes.shutdown(); }
    }
}
