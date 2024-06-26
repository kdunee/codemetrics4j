package org.codemetrics4j.metrics.calculators

import static org.codemetrics4j.util.Matchers.containsMetric
import static org.codemetrics4j.util.TestUtil.methodFromSnippet
import static spock.util.matcher.HamcrestSupport.expect

import org.codemetrics4j.metrics.MetricName
import spock.lang.Specification

class NumberOfParametersCalculatorSpec extends Specification {
	NumberOfParametersCalculator unit

	def setup() {
		unit = new NumberOfParametersCalculator()
	}

	def "calculate simple metric"() {

		given:
		def type = methodFromSnippet '''
                public void method() {

                }
        '''

		when:
		def result = unit.calculate(type)

		then:
		expect result, containsMetric(MetricName.NOP, 0)
	}

	def "calculate simple metric with parameters"() {

		given:
		def type = methodFromSnippet '''
                public void method(String one, int two, double three) {

                }
        '''

		when:
		def result = unit.calculate(type)

		then:
		expect result, containsMetric(MetricName.NOP, 3)
	}
}
