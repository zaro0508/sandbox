package org.sagebionetworks.bridge.play.modules;

import java.util.Map;
import java.util.Map.Entry;

import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.dynamodb.DynamoInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import play.mvc.Controller;

import com.google.inject.AbstractModule;
import com.google.inject.Binder;

/**
 * <p>
 * Play module to initialize our environment (notably Dynamo tables and Spring). This is the abstract base Bridge
 * module. For our production module used for launching actual Bridge servers, see
 * {@link BridgeProductionSpringContextModule}. For our test module used for unit tests, see
 * BridgeTestSpringContextModule in unit tests.
 * </p>
 * <p>
 * The subclasses need to be full-fledged classes, because even though they differ only on a single string, there's no
 * way to pass this string into Play without creating an entirely separate module class.
 * </p>
 */
public abstract class BridgeSpringContextModule extends AbstractModule {
    private static final Logger logger = LoggerFactory.getLogger(BridgeSpringContextModule.class);

    @Override
    protected void configure() {
        logger.info("Environment: " + BridgeConfigFactory.getConfig().getEnvironment().name());
        loadDynamo();
        final AbstractApplicationContext appContext = loadAppContext();
        bindControllers(appContext);
    }

    private void loadDynamo() {
        DynamoInitializer.init("org.sagebionetworks.bridge.dynamodb");
        logger.info("DynamoDB tables loaded.");
    }

    private AbstractApplicationContext loadAppContext() {
        final AbstractApplicationContext bridgeAppContext =
                new ClassPathXmlApplicationContext(getSpringXmlFilename());
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                bridgeAppContext.stop();
                bridgeAppContext.close();
            }
        });
        bridgeAppContext.start();
        logger.info("Bridge Spring context loaded.");
        return bridgeAppContext;
    }

    private void bindControllers(final AbstractApplicationContext appContext) {
        final Binder spBinder = binder();
        final BeanFactory beanFactory = appContext.getBeanFactory();
        final Map<String, Controller> controllers = appContext.getBeansOfType(Controller.class);
        for (final Entry<String, Controller> entry : controllers.entrySet()) {
            final Class<?> clazz = entry.getValue().getClass();
            bindBean(spBinder, beanFactory, entry.getKey(), clazz);
        }
    }

    private <T> void bindBean(final Binder binder, final BeanFactory beanFactory,
            final String name, final Class<T> clazz) {
        if (!name.endsWith("Proxied")) {
            binder.bind(clazz).toInstance(clazz.cast(
                    beanFactory.getBean(clazz.getSimpleName() + "Proxied")));
        }
        logger.info(clazz.getName() + " is bound.");
    }

    /**
     * XML filename, as found in the conf directory, that's the root of our Spring config. Subclasses should override
     * this to get specific production vs unit test configs.
     */
    protected abstract String getSpringXmlFilename();
}
