package com.blibli.oss.webclient.configuration;

import com.blibli.oss.webclient.annotation.ApiClient;
import com.blibli.oss.webclient.annotation.EnableApiClient;
import com.blibli.oss.webclient.bean.ApiClientMethodInterceptor;
import com.blibli.oss.webclient.properties.ApiClientProperties;
import lombok.Setter;
import org.springframework.aop.framework.ProxyFactoryBean;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@EnableConfigurationProperties({
	ApiClientProperties.class
})
public class ApiClientConfiguration implements ImportBeanDefinitionRegistrar, ResourceLoaderAware, EnvironmentAware, ApplicationContextAware {

	public static final String METHOD_INTERCEPTOR = "MethodInterceptor";

	@Setter
	private ApplicationContext applicationContext;

	@Setter
	private ResourceLoader resourceLoader;

	@Setter
	private Environment environment;

	@Override
	public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
		ClassPathScanningCandidateComponentProvider scanner = getScanner();
		scanner.setResourceLoader(resourceLoader);

		Map<String, Object> attrs = metadata.getAnnotationAttributes(EnableApiClient.class.getName());
		AnnotationTypeFilter annotationTypeFilter = new AnnotationTypeFilter(ApiClient.class);
		scanner.addIncludeFilter(annotationTypeFilter);
		Set<String> basePackages = getBasePackages(metadata);

		for (String basePackage : basePackages) {
			Set<BeanDefinition> candidateComponents = scanner.findCandidateComponents(basePackage);
			for (BeanDefinition candidateComponent : candidateComponents) {
				if (candidateComponent instanceof AnnotatedBeanDefinition) {
					// verify annotated class is an interface
					AnnotatedBeanDefinition beanDefinition = (AnnotatedBeanDefinition) candidateComponent;
					AnnotationMetadata annotationMetadata = beanDefinition.getMetadata();
					Assert.isTrue(annotationMetadata.isInterface(), "@ApiClient can only be specified on an interface");

					Map<String, Object> attributes = annotationMetadata.getAnnotationAttributes(ApiClient.class.getCanonicalName());

					String name = getBeanName(attributes);
					registerApiClientInterceptor(registry, name, annotationMetadata, attributes);
					registerApiClient(registry, name, annotationMetadata, attributes);
				}
			}
		}
	}

	private void registerApiClient(BeanDefinitionRegistry registry, String name,
																 AnnotationMetadata annotationMetadata, Map<String, Object> attributes) {
		String beanName = annotationMetadata.getClassName();
		BeanDefinitionBuilder definition = BeanDefinitionBuilder.genericBeanDefinition(ProxyFactoryBean.class);
		definition.addPropertyValue("interceptorNames", new String[]{name + "Interceptor"});
		try {
			definition.addPropertyValue("proxyInterfaces", new Class[]{Class.forName(beanName)});
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
		definition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);

		AbstractBeanDefinition beanDefinition = definition.getBeanDefinition();
		boolean primary = (Boolean) attributes.get("primary");
		beanDefinition.setPrimary(primary);

		BeanDefinitionHolder holder = new BeanDefinitionHolder(beanDefinition, beanName, new String[]{name});
		BeanDefinitionReaderUtils.registerBeanDefinition(holder, registry);
	}

	private void registerApiClientInterceptor(BeanDefinitionRegistry registry, String name,
																						AnnotationMetadata annotationMetadata, Map<String, Object> attributes) {
		String beanName = annotationMetadata.getClassName() + METHOD_INTERCEPTOR;
		String aliasName = name + METHOD_INTERCEPTOR;

		BeanDefinitionBuilder definition = BeanDefinitionBuilder.genericBeanDefinition(ApiClientMethodInterceptor.class);
		definition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);

		AbstractBeanDefinition beanDefinition = definition.getBeanDefinition();
		boolean primary = (Boolean) attributes.get("primary");
		beanDefinition.setPrimary(primary);

		BeanDefinitionHolder holder = new BeanDefinitionHolder(beanDefinition, beanName, new String[]{aliasName});
		BeanDefinitionReaderUtils.registerBeanDefinition(holder, registry);
	}

	private String getBeanName(Map<String, Object> client) {
		String value = (String) client.get("name");
		if (StringUtils.hasText(value)) {
			return value;
		}
		throw new IllegalStateException("'name' must be provided in @" + ApiClient.class.getSimpleName());
	}

	protected Set<String> getBasePackages(AnnotationMetadata importingClassMetadata) {
		Map<String, Object> attributes = importingClassMetadata.getAnnotationAttributes(EnableApiClient.class.getCanonicalName());
		Set<String> basePackages = new HashSet<>();
		for (String pkg : (String[]) attributes.get("basePackages")) {
			if (StringUtils.hasText(pkg)) {
				basePackages.add(pkg);
			}
		}
		return basePackages;
	}

	protected ClassPathScanningCandidateComponentProvider getScanner() {
		return new ClassPathScanningCandidateComponentProvider(false, this.environment) {
			@Override
			protected boolean isCandidateComponent(
				AnnotatedBeanDefinition beanDefinition) {
				boolean isCandidate = false;
				if (beanDefinition.getMetadata().isIndependent()) {
					if (!beanDefinition.getMetadata().isAnnotation()) {
						isCandidate = true;
					}
				}
				return isCandidate;
			}
		};
	}
}
