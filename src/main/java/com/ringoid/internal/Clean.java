package com.ringoid.internal;

import com.amazonaws.services.lambda.runtime.Context;
import org.neo4j.driver.v1.AuthTokens;
import org.neo4j.driver.v1.Config;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.Transaction;
import org.neo4j.driver.v1.TransactionWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class Clean {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final Driver driver;
    private final String env;

    private static final String DELETE_EVERYTHING_REQUEST = "MATCH (n) detach delete n";

    public Clean() {
        String neo4jUri = System.getenv("NEO4J_URI");
        //todo:read these value from Security Storage
        String userName = System.getenv("NEO4J_USER");
        String password = System.getenv("NEO4J_PASSWORD");
        env = System.getenv("ENV");
        driver = GraphDatabase.driver(neo4jUri, AuthTokens.basic(userName, password),
                Config.build().withMaxTransactionRetryTime(10, TimeUnit.SECONDS).toConfig());
    }

    public void handler(Object request, Context context) {
        //!!!Start block : Very important, don't modify
        if (!Objects.equals("test", env)) {
            log.error("clean DB in not safe env");
            throw new RuntimeException("clean DB in not safe env");
        }
        //!!!Finish block : Very important, don't modify

        log.debug("clean all DB");
        final Map<String, Object> parameters = new HashMap<>();
        try (Session session = driver.session()) {
            session.readTransaction(new TransactionWork<Integer>() {
                @Override
                public Integer execute(Transaction tx) {
                    tx.run(DELETE_EVERYTHING_REQUEST, parameters);
                    return 1;
                }
            });
            log.info("successfully clean all DB");
        } catch (Throwable throwable) {
            log.error("error clean all DB", throwable);
            throw throwable;
        }
    }

}
