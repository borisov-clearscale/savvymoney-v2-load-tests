package com.sm.lt;

import static com.sm.lt.infrastructure.configuration.TestVariableSetting.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import lombok.extern.slf4j.Slf4j;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.sm.lt.api.Session;
import com.sm.lt.api.User;
import com.sm.lt.infrastructure.configuration.Configuration;
import com.sm.lt.infrastructure.configuration.ConfigurationParser;
import com.sm.lt.infrastructure.configuration.ConfigurationUtils;
import com.sm.lt.infrastructure.jmeter.JMeterTestExecutor;
import com.sm.lt.infrastructure.junit.CurrentEnvironmentSetter;
import com.sm.lt.infrastructure.junit.CurrentTestFiles;
import com.typesafe.config.Config;

@Slf4j
public class ProductionLikeTest {

    private static final String JMETER_TEST_PLAN = "production_like/test_plan.jmx";
    private static final String TEST_PLAN_CONFIGURATION = "production_like/test_plan.conf";

    private static final Configuration CONFIGURATION = ConfigurationUtils.getConfiguration(TEST_PLAN_CONFIGURATION);
    private static final Map<String, String> VARIABLES = CONFIGURATION.getVariables(ImmutableList.of(
            var("ProductionLikeTest", "regGroupNumberOfThreads"),
            var("ProductionLikeTest", "regGroupRumpUpPeriod"),
            var("ProductionLikeTest", "regGroupLoopCount"),
            var("ProductionLikeTest", "unregGroupNumberOfThreads"),
            var("ProductionLikeTest", "unregGroupRumpUpPeriod"),
            var("ProductionLikeTest", "unregGroupLoopCount"),
            var("ProductionLikeTest", "thinkTime")));

    @ClassRule
    public static final CurrentEnvironmentSetter currentEnvironmentSetter = new CurrentEnvironmentSetter(CONFIGURATION);

    @Rule
    public CurrentTestFiles currentTestFiles = new CurrentTestFiles();

    @Test
    public void test() throws Exception {
        List<User> regUsers = ConfigurationParser.getUsersWithResolving(CONFIGURATION.get("regUsers", Config::getConfig));
        List<Session> regSessions = regUsers
                .stream()
                .map(User::configureAsRegisteredUser)
                .map(Session::start)
                .collect(Collectors.toList());
        List<User> unregUsers = ConfigurationParser.getUsersWithResolving(CONFIGURATION.get("unregUsers", Config::getConfig));
        List<Session> unregSessions = Lists.transform(unregUsers, Session::start);

        currentTestFiles.saveToTestFolder("reg_data.csv",
                regSessions.stream().map(Session::getSmToken).collect(Collectors.joining("\n")));
        currentTestFiles.saveToTestFolder("unreg_data.csv",
                unregSessions.stream().map(Session::getSmToken).collect(Collectors.joining("\n")));
        Path testPlan = currentTestFiles.copyToTestFolder("test_plan.jmx", JMETER_TEST_PLAN);
        JMeterTestExecutor
                .builder()
                .variables(VARIABLES)
                .testPlan(testPlan)
                .testFolder(currentTestFiles.getTestFolder())
                .resultsFolder(currentTestFiles.getResultsFolder())
                .build()
                .run();
    }
}