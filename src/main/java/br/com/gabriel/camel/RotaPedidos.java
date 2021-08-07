package br.com.gabriel.camel;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;

public class RotaPedidos {

    public static void main(String[] args) throws Exception {
        CamelContext context = new DefaultCamelContext();

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(
                        deadLetterChannel("file:erro").
                                logExhaustedMessageHistory(true).
                                maximumRedeliveries(3).
                                redeliveryDelay(5000).
                                onRedelivery(exchange -> {
                                    int counter = (int) exchange.getIn().getHeader(Exchange.REDELIVERY_COUNTER);
                                    int max = (int) exchange.getIn().getHeader(Exchange.REDELIVERY_MAX_COUNTER);
                                    System.out.println("Redelivery - " + counter + "/" + max );
                                })
                );

                from("file:pedidos?delay=5s&noop=true").
                        to("validator:pedido.xsd").
                        routeId("rota-pedidos").
                        multicast().
                            to("direct:soap").
                            to("direct:http");

                from("direct:soap").
                        routeId("rota-soap").
                        to("xslt:pedido-para-soap.xslt").
                        log("Resultado do template: ${body}").
                        setHeader(Exchange.CONTENT_TYPE, constant("text/xml")).
                        to("http4://localhost:8080/webservices/financeiro");

                from("direct:http").
                        routeId("rota-http").
                        setProperty("pedidoId", xpath("/pedido/id/text()")).
                        setProperty("email", xpath("/pedido/pagamento/email-titular/text()")).
                        split().
                        xpath("/pedido/itens/item").
                        filter().
                        xpath("/item/formato[text()='EBOOK']").
                        setProperty("ebookId", xpath("/item/livro/codigo/text()")).
                        setHeader(Exchange.HTTP_QUERY,
                                simple("clienteId=${property.email}&pedidoId=${property.pedidoId}&ebookId=${property.ebookId}")).
                        to("http4://localhost:8080/webservices/ebook/item");
            }
        });

        context.start();
        Thread.sleep(20000);
        context.stop();
    }
}
