package org.codemetrics4j.metrics.calculators;

import com.github.javaparser.Position;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.google.common.collect.ImmutableSet;
import java.util.Optional;
import java.util.Set;
import org.codemetrics4j.input.Type;
import org.codemetrics4j.metrics.Calculator;
import org.codemetrics4j.metrics.Metric;
import org.codemetrics4j.metrics.MetricName;

/**
 * Counts the raw number of lines of code within a class (excludes package
 * declaration, import statements, and comments outside a class).  Within a
 * class declaration, will count whitespace, comments, multi-line statements,
 * and brackets.
 *
 * @author Rod Hilton
 * @since 0.1
 */
public class RawTotalLinesOfCodeCalculator implements Calculator<Type> {

    @Override
    public Set<Metric> calculate(Type type) {

        ClassOrInterfaceDeclaration decl = type.getSource();

        Optional<Position> end = decl.getEnd();
        Optional<Position> begin = decl.getBegin();

        return begin.map(value -> end.<Set<Metric>>map(position -> ImmutableSet.of(
                                Metric.of(MetricName.RTLOC, "Raw Total Lines of Code", position.line - value.line + 1)))
                        .orElseGet(ImmutableSet::of))
                .orElseGet(ImmutableSet::of);
    }
}
