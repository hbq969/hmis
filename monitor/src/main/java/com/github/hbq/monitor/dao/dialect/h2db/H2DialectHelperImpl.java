package com.github.hbq.monitor.dao.dialect.h2db;

import com.github.hbq.common.spring.context.SpringContext;
import com.github.hbq.monitor.dao.dialect.DialectHelper;
import com.github.hbq.monitor.dao.dialect.QuotaDataDao;
import com.github.hbq.monitor.dao.dialect.manage.DialectManage;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.tools.Server;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * @author hbq
 */
@ConditionalOnProperty(prefix = "hbq.monitor", name = "dialect", havingValue = "h2db")
@Component("monitor-dao-dialect-H2dbDialectImpl")
@Slf4j
public class H2DialectHelperImpl implements DialectHelper, InitializingBean, DisposableBean {

  public static final String DIALECT_H2 = "h2db";

  @Autowired
  private SpringContext context;

  @Autowired
  private DialectManage dialectManage;

  private SqlSessionTemplate sqlSessionTemplate;

  private org.h2.tools.Server server;

  @Override
  public void afterPropertiesSet() throws Exception {
    bootstrap();
    dialectManage.registry(getKey(), this);
    createQuotaData();
  }

  @Override
  public void bootstrap() throws Exception {
    log.info("初始化h2数据库来存储指标数据");
    BasicDataSource ds = new BasicDataSource();
    ds.setDriverClassName("org.h2.Driver");
    ds.setUrl(context.getProperty("hbq.monitor.dialect.h2db.url"));
    ds.setUsername(context.getProperty("hbq.monitor.dialect.h2db.username"));
    ds.setPassword(context.getProperty("hbq.monitor.dialect.h2db.password"));
    ds.setMaxWaitMillis(context.getLongValue("hbq.monitor.dialect.h2db.max-wait-mills", 300000));
    ds.setMaxTotal(context.getIntValue("hbq.monitor.dialect.h2db.max-total", 20));
    ds.setValidationQuery("select 1");

    SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
    factoryBean.setDataSource(ds);
    factoryBean.setConfigLocation(new ClassPathResource("jpaConfig.xml"));
    PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
    List<Resource> list = new ArrayList<>();
    String[] paths = {"classpath*:com/**/dialect/h2db/**/*Mapper.xml", "classpath*:com/**/dialect/common/**/*Mapper.xml"};
    for (String path : paths) {
      Resource[] resources = resolver.getResources(path);
      for (Resource resource : resources) {
        list.add(resource);
      }
    }
    Resource[] array = new Resource[list.size()];
    list.toArray(array);
    factoryBean.setMapperLocations(array);
    SqlSessionFactory factory = factoryBean.getObject();
    sqlSessionTemplate = new SqlSessionTemplate(factory);

    log.info("初始化h2完成: {}", getMapper(QuotaDataDao.class).test());

    CompletableFuture.runAsync(() -> {
      String tcpPort = context.getProperty("hbq.monitor.dialect.h2db.tcp-port", "21003");
      String[] args = new String[]{"-tcpPort", tcpPort, "-tcpAllowOthers"};
      try {
        server = Server.createTcpServer(args).start();
        log.info("启动h2数据成功");
      } catch (SQLException ex) {
        log.error("启动h2数据库tcp监听异常", ex);
      }
    });
  }

  @Override
  public String getKey() {
    return DIALECT_H2;
  }

  @Override
  public <T> T getMapper(Class<T> clz) {
    return sqlSessionTemplate.getMapper(clz);
  }

  @Override
  public void destroy() throws Exception {
    if (Objects.nonNull(server)) {
      log.info("停止h2数据...");
      server.shutdown();
    }
  }

  private void createQuotaData() {
    try {
      getMapper(QuotaDataDao.class).createQuotaData();
      log.info("创建指标数据表成功");
    } catch (Exception e) {
      log.info("指标数据表已经存在");
    }
  }
}
