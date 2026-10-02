package com.coffee.module.payment.biz.application.service;

import com.coffee.common.core.exception.ServiceException;
import com.coffee.module.order.api.OrderPaymentService;
import com.coffee.module.order.api.OrderQueryService;
import com.coffee.module.payment.api.PaymentService;
import com.coffee.module.payment.api.dto.PaymentResponse;
import com.coffee.module.payment.biz.domain.Payment;
import com.coffee.module.payment.biz.domain.repository.PaymentRepository;
import com.coffee.module.payment.biz.domain.service.PaymentChannel;
import com.coffee.module.payment.biz.domain.service.channel.MockPaymentChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringJUnitConfig(PaymentAtomicityIntegrationTest.Config.class)
class PaymentAtomicityIntegrationTest {
    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private AtomicBoolean failOrderTransition;

    @BeforeEach
    void resetDatabase() {
        failOrderTransition.set(false);
        jdbc.update("DELETE FROM test_payment");
        jdbc.update("DELETE FROM test_order");
        jdbc.update("""
                INSERT INTO test_payment
                    (id, payment_no, order_id, channel, amount, status, transaction_no)
                VALUES (1, 'pay-1', 7, 'MOCK', 32.00, 'PENDING', NULL)
                """);
        jdbc.update("INSERT INTO test_order (id, status) VALUES (7, 'UNPAID')");
    }

    @Test
    void mockPaymentCommitsPaymentAndOrderStatusesTogether() {
        PaymentResponse response = paymentService.pay("pay-1", "MOCK");

        assertEquals("PAID", response.getStatus());
        assertEquals("PAID", paymentStatus());
        assertEquals("PENDING", orderStatus());
        org.junit.jupiter.api.Assertions.assertNotNull(transactionNo());
    }

    @Test
    void orderTransitionFailureRollsBackPaymentStatusAndTransactionNo() {
        failOrderTransition.set(true);

        assertThrows(ServiceException.class, () -> paymentService.pay("pay-1", "MOCK"));

        assertEquals("PENDING", paymentStatus());
        assertNull(transactionNo());
        assertEquals("UNPAID", orderStatus());
    }

    @Test
    void callbackOrderTransitionFailureRollsBackPaymentWrite() {
        failOrderTransition.set(true);

        assertThrows(ServiceException.class,
                () -> paymentService.handleCallback("MOCK", "pay-1", "txn-1"));

        assertEquals("PENDING", paymentStatus());
        assertNull(transactionNo());
        assertEquals("UNPAID", orderStatus());
    }

    @Test
    void duplicateSuccessfulCallbackLeavesBothStatusesUnchanged() {
        paymentService.handleCallback("MOCK", "pay-1", "txn-1");
        PaymentResponse replay = paymentService.handleCallback("MOCK", "pay-1", "txn-1");

        assertEquals("PAID", replay.getStatus());
        assertEquals("PAID", paymentStatus());
        assertEquals("PENDING", orderStatus());
    }

    private String paymentStatus() {
        return jdbc.queryForObject("SELECT status FROM test_payment WHERE id=1", String.class);
    }

    private String transactionNo() {
        return jdbc.queryForObject("SELECT transaction_no FROM test_payment WHERE id=1", String.class);
    }

    private String orderStatus() {
        return jdbc.queryForObject("SELECT status FROM test_order WHERE id=7", String.class);
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {
        @Bean(destroyMethod = "shutdown")
        EmbeddedDatabase dataSource() {
            EmbeddedDatabase database = new EmbeddedDatabaseBuilder()
                    .setType(EmbeddedDatabaseType.H2)
                    .build();
            JdbcTemplate jdbc = new JdbcTemplate(database);
            jdbc.execute("""
                    CREATE TABLE test_payment (
                        id BIGINT PRIMARY KEY,
                        payment_no VARCHAR(40) NOT NULL,
                        order_id BIGINT NOT NULL,
                        user_id BIGINT NULL,
                        channel VARCHAR(16),
                        amount DOUBLE PRECISION NOT NULL,
                        status VARCHAR(16) NOT NULL,
                        transaction_no VARCHAR(100),
                        paid_at TIMESTAMP NULL,
                        created_at TIMESTAMP NULL,
                        updated_at TIMESTAMP NULL
                    )
                    """);
            jdbc.execute("CREATE TABLE test_order (id BIGINT PRIMARY KEY, status VARCHAR(16) NOT NULL)");
            return database;
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        AtomicBoolean failOrderTransition() {
            return new AtomicBoolean();
        }

        @Bean
        PaymentRepository paymentRepository(JdbcTemplate jdbc) {
            return new JdbcPaymentRepository(jdbc);
        }

        @Bean
        PaymentStateService paymentStateService(PaymentRepository repository) {
            return new PaymentStateService(repository);
        }

        @Bean
        OrderQueryService orderQueryService() {
            return org.mockito.Mockito.mock(OrderQueryService.class);
        }

        @Bean
        OrderPaymentService orderPaymentService(JdbcTemplate jdbc, AtomicBoolean failOrderTransition) {
            return new OrderPaymentService() {
                @Override
                public void checkPayable(Long orderId) {
                    String status = jdbc.queryForObject(
                            "SELECT status FROM test_order WHERE id=?", String.class, orderId);
                    if (!"UNPAID".equals(status)) {
                        throw new ServiceException(409, "Order is not payable");
                    }
                }

                @Override
                public boolean markPaid(Long orderId) {
                    if (failOrderTransition.get()) return false;
                    return jdbc.update(
                            "UPDATE test_order SET status='PENDING' WHERE id=? AND status='UNPAID'",
                            orderId) == 1;
                }
            };
        }

        @Bean
        PaymentChannel mockPaymentChannel() {
            return new MockPaymentChannel();
        }

        @Bean
        PaymentServiceImpl paymentService(PaymentRepository repository,
                                          OrderQueryService orderQueryService,
                                          OrderPaymentService orderPaymentService,
                                          PaymentStateService paymentStateService,
                                          List<PaymentChannel> channels) {
            return new PaymentServiceImpl(repository, orderQueryService, orderPaymentService,
                    paymentStateService, channels);
        }
    }

    private static final class JdbcPaymentRepository implements PaymentRepository {
        private final JdbcTemplate jdbc;

        private JdbcPaymentRepository(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        @Override
        public Payment save(Payment payment) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Payment findById(Long id) {
            return find("SELECT * FROM test_payment WHERE id=?", id);
        }

        @Override
        public Payment findByPaymentNo(String paymentNo) {
            return find("SELECT * FROM test_payment WHERE payment_no=?", paymentNo);
        }

        @Override
        public Payment findByOrderId(Long orderId) {
            return find("SELECT * FROM test_payment WHERE order_id=?", orderId);
        }

        private Payment find(String sql, Object value) {
            return jdbc.query(sql, rs -> {
                if (!rs.next()) return null;
                Payment payment = new Payment();
                payment.setId(rs.getLong("id"));
                payment.setPaymentNo(rs.getString("payment_no"));
                payment.setOrderId(rs.getLong("order_id"));
                long userId = rs.getLong("user_id");
                payment.setUserId(rs.wasNull() ? null : userId);
                payment.setChannel(rs.getString("channel"));
                payment.setAmount(rs.getDouble("amount"));
                payment.setStatus(Payment.PaymentStatus.valueOf(rs.getString("status")));
                payment.setTransactionNo(rs.getString("transaction_no"));
                Timestamp paidAt = rs.getTimestamp("paid_at");
                if (paidAt != null) payment.setPaidAt(paidAt.toLocalDateTime());
                Timestamp createdAt = rs.getTimestamp("created_at");
                if (createdAt != null) payment.setCreatedAt(createdAt.toLocalDateTime());
                Timestamp updatedAt = rs.getTimestamp("updated_at");
                if (updatedAt != null) payment.setUpdatedAt(updatedAt.toLocalDateTime());
                return payment;
            }, value);
        }

        @Override
        public boolean tryStartProcessing(Long paymentId) {
            return jdbc.update("UPDATE test_payment SET status='PROCESSING' WHERE id=? AND status='PENDING'",
                    paymentId) == 1;
        }

        @Override
        public boolean markPaidIfProcessing(Long paymentId, String channel, String transactionNo,
                                            LocalDateTime paidAt) {
            return jdbc.update("""
                    UPDATE test_payment SET channel=?, transaction_no=?, status='PAID', paid_at=?, updated_at=?
                    WHERE id=? AND status='PROCESSING'
                    """, channel, transactionNo, paidAt, paidAt, paymentId) == 1;
        }

        @Override
        public boolean markPaidIfPendingOrProcessing(Long paymentId, String channel, String transactionNo,
                                                     LocalDateTime paidAt) {
            return jdbc.update("""
                    UPDATE test_payment SET channel=?, transaction_no=?, status='PAID', paid_at=?, updated_at=?
                    WHERE id=? AND status IN ('PENDING', 'PROCESSING')
                    """, channel, transactionNo, paidAt, paidAt, paymentId) == 1;
        }

        @Override
        public boolean resetProcessing(Long paymentId) {
            return jdbc.update("UPDATE test_payment SET status='PENDING' WHERE id=? AND status='PROCESSING'",
                    paymentId) == 1;
        }

        @Override
        public boolean markRefundedIfPaid(Long paymentId) {
            return jdbc.update("UPDATE test_payment SET status='REFUNDED' WHERE id=? AND status='PAID'",
                    paymentId) == 1;
        }
    }
}
