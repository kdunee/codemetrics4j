package org.codemetrics4j.metrics.calculators;

import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.SimpleName;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.graph.Graph;
import java.util.*;
import java.util.stream.Collectors;
import org.codemetrics4j.input.Method;
import org.codemetrics4j.input.Type;
import org.codemetrics4j.metrics.Calculator;
import org.codemetrics4j.metrics.Metric;
import org.codemetrics4j.metrics.MetricName;
import org.codemetrics4j.metrics.value.NumericValue;
import org.codemetrics4j.util.CalculationUtils;

public class MethodAndAttributeInheritanceCalculator implements Calculator<Type> {

    @Override
    public Set<Metric> calculate(Type type) {
        Graph<Type> inheritanceGraph =
                type.getParentPackage().getParentProject().getMetadata().getInheritanceGraph();

        ClassOrInterfaceDeclaration declaration = type.getSource();

        Set<Type> ancestors = new HashSet<>();

        Set<Type> parents = inheritanceGraph.predecessors(type);

        Stack<Type> typesToCheck = new Stack<>();
        typesToCheck.addAll(parents);

        while (!typesToCheck.empty()) {
            Type typeToCheck = typesToCheck.pop();
            ancestors.add(typeToCheck);

            typesToCheck.addAll(inheritanceGraph.predecessors(typeToCheck));
        }

        Set<Method> inheritableMethods = ancestors.stream()
                .flatMap(p -> p.getMethods().stream())
                .filter(method -> {
                    // We only want to count a method as inherited if it's a parent method that has an implementation
                    // In other words we want to exclude anything on an interface unless it's got a default impl
                    // And we want to exclude any abstract methods
                    boolean isDefinedOnAbstractClass =
                            method.getParentType().getSource().isAbstract();
                    boolean isAbstract =
                            isDefinedOnAbstractClass && method.getSource().isAbstract();
                    boolean isDefinedOnInterface =
                            method.getParentType().getSource().isInterface();
                    boolean isDefaultImpl =
                            isDefinedOnInterface && method.getSource().isDefault();

                    if (isDefinedOnInterface) {
                        return isDefaultImpl;
                    } else if (isDefinedOnAbstractClass) {
                        return !isAbstract;
                    } else {
                        return true;
                    }
                })
                .collect(Collectors.toSet());

        Set<Method> definedMethods = type.getMethods().stream()
                .filter(method -> {
                    boolean isDefinedOnAbstractClass =
                            method.getParentType().getSource().isAbstract();
                    boolean isAbstract =
                            isDefinedOnAbstractClass && method.getSource().isAbstract();
                    boolean isDefinedOnInterface =
                            method.getParentType().getSource().isInterface();
                    boolean isDefaultImpl =
                            isDefinedOnInterface && method.getSource().isDefault();

                    if (isAbstract) {
                        return false;
                    } else return !isDefinedOnInterface || isDefaultImpl;
                })
                .collect(Collectors.toSet());

        Set<String> inheritedMethodSignatures = inheritableMethods.stream()
                .map(im -> im.getSource().getSignature().asString())
                .collect(Collectors.toSet());

        Set<Method> overriddenMethods = definedMethods.stream()
                .filter(dm -> inheritedMethodSignatures.contains(
                        dm.getSource().getSignature().asString()))
                .collect(Collectors.toSet());

        Set<String> overriddenMethodSignatures = overriddenMethods.stream()
                .map(im -> im.getSource().getSignature().asString())
                .collect(Collectors.toSet());

        Set<Method> inheritedAndNotOverriddenMethods = inheritableMethods.stream()
                .filter(im -> !overriddenMethodSignatures.contains(
                        im.getSource().getSignature().asString()))
                .filter(im -> !im.getSource().isPrivate())
                .collect(Collectors.toSet());

        Set<Method> allMethods = Sets.union(definedMethods, inheritedAndNotOverriddenMethods);

        Set<Method> publicDefinedMethods =
                definedMethods.stream().filter(dm -> dm.getSource().isPublic()).collect(Collectors.toSet());

        Set<Method> publicInheritedNotOverriddenMethods = inheritedAndNotOverriddenMethods.stream()
                .filter(dm -> dm.getSource().isPublic())
                .collect(Collectors.toSet());

        Set<Method> hiddenInheritedNotOverridden =
                Sets.difference(inheritedAndNotOverriddenMethods, publicInheritedNotOverriddenMethods).stream()
                        .filter(method -> method.getSource().isDefault()
                                || method.getSource()
                                        .isProtected()) // Private methods aren't 'inherited' because they can't
                        // be called
                        .collect(Collectors.toSet());

        Set<Method> hiddenDefined = Sets.difference(definedMethods, publicDefinedMethods);

        ImmutableSet.Builder<Metric> metricBuilder = ImmutableSet.<Metric>builder()
                .add(Metric.of(MetricName.Mit, "Number of Methods Inherited (Total)", inheritableMethods.size()))
                .add(Metric.of(
                        MetricName.Mi,
                        "Number of Methods Inherited and Not Overridden",
                        inheritedAndNotOverriddenMethods.size()))
                .add(Metric.of(MetricName.Md, "Number of Methods Defined", definedMethods.size()))
                .add(Metric.of(MetricName.Mo, "Number of Methods Overridden", overriddenMethods.size()))
                .add(Metric.of(MetricName.Ma, "Number of Methods (All)", allMethods.size()))
                .add(Metric.of(
                        MetricName.PMi,
                        "Number of Public Methods Inherited and Not Overridden",
                        publicInheritedNotOverriddenMethods.size()))
                .add(Metric.of(MetricName.PMd, "Number of Public Methods Defined", publicDefinedMethods.size()))
                .add(Metric.of(
                        MetricName.HMi,
                        "Number of Hidden Methods Inherited and Not Overridden",
                        hiddenInheritedNotOverridden.size()))
                .add(Metric.of(MetricName.HMd, "Number of Hidden Methods Defined", hiddenDefined.size()));

        if (!inheritableMethods.isEmpty()) {
            metricBuilder.add(Metric.of(
                    MetricName.NMIR,
                    "Number of Methods Inherited Ratio",
                    NumericValue.ofRational(inheritedAndNotOverriddenMethods.size(), inheritableMethods.size())
                            .times(NumericValue.of(100))));
        }

        if (!allMethods.isEmpty()) {
            metricBuilder.add(Metric.of(
                    MetricName.MIF,
                    "Method Inheritance Factor",
                    NumericValue.of(inheritedAndNotOverriddenMethods.size())
                            .divide(NumericValue.of(allMethods.size()))));
            NumericValue publicMethods = NumericValue.of(publicInheritedNotOverriddenMethods.size())
                    .plus(NumericValue.of(publicDefinedMethods.size()));
            metricBuilder.add(Metric.of(
                    MetricName.PMR, "Public Methods Ratio", publicMethods.divide(NumericValue.of(allMethods.size()))));
        }

        if (!definedMethods.isEmpty()) {
            metricBuilder.add(Metric.of(
                    MetricName.MHF,
                    "Method Hiding Factor",
                    NumericValue.of(publicDefinedMethods.size()).divide(NumericValue.of(definedMethods.size()))));
        }

        Set<Attribute> inheritableAttributes = ancestors.stream()
                .flatMap(p -> getFlattenedAttributes(p).stream())
                .filter(attribute -> !attribute.isPrivate())
                .collect(Collectors.toSet());

        Set<Attribute> definedAttributes = getFlattenedAttributes(type);

        Set<Attribute> inheritedNotOverriddenAttributes = Sets.difference(inheritableAttributes, definedAttributes);
        Set<Attribute> overriddenAttributes = Sets.intersection(inheritableAttributes, definedAttributes);
        Set<Attribute> allAttributes = Sets.union(definedAttributes, inheritedNotOverriddenAttributes);

        Set<Attribute> publicDefinedAttributes =
                definedAttributes.stream().filter(Attribute::isPublicish).collect(Collectors.toSet());

        metricBuilder
                .add(Metric.of(MetricName.Ait, "Number of Attributes Inherited (Total)", inheritableAttributes.size()))
                .add(Metric.of(
                        MetricName.Ai,
                        "Number of Attributes Inherited and Not Overridden",
                        inheritedNotOverriddenAttributes.size()))
                .add(Metric.of(MetricName.Ad, "Number of Attributes Defined", definedAttributes.size()))
                .add(Metric.of(MetricName.Ao, "Number of Attributes Overridden", overriddenAttributes.size()))
                .add(Metric.of(MetricName.Aa, "Number of Attributes (All)", allAttributes.size()))
                .add(Metric.of(MetricName.Av, "Number of Public Attributes Defined", publicDefinedAttributes.size()));

        if (!allAttributes.isEmpty()) {
            metricBuilder.add(Metric.of(
                    MetricName.AIF,
                    "Attribute Inheritance Factor",
                    NumericValue.of(inheritedNotOverriddenAttributes.size())
                            .divide(NumericValue.of(allAttributes.size()))));
        }

        if (!definedAttributes.isEmpty()) {
            metricBuilder.add(Metric.of(
                    MetricName.AHF,
                    "Attribute Hiding Factor",
                    NumericValue.of(publicDefinedAttributes.size()).divide(NumericValue.of(definedAttributes.size()))));
        }

        return metricBuilder.build();
    }

    private Set<Attribute> getFlattenedAttributes(Type type) {
        Set<Attribute> attributes = new HashSet<>();
        List<FieldDeclaration> declarations = type.getSource().getFields();
        for (FieldDeclaration declaration : declarations) {
            Set<Modifier.Keyword> modifiers = CalculationUtils.getModifierKeywords(declaration);
            NodeList<VariableDeclarator> variables = declaration.getVariables();
            for (VariableDeclarator variableDeclarator : variables) {
                com.github.javaparser.ast.type.Type variableType = variableDeclarator.getType();
                SimpleName name = variableDeclarator.getName();
                attributes.add(new Attribute(type, modifiers, variableType, name));
            }
        }

        return ImmutableSet.copyOf(attributes);
    }

    private static class Attribute {

        private final Type parentType;
        private final Set<Modifier.Keyword> modifiers;
        private final com.github.javaparser.ast.type.Type variableType;
        private final SimpleName name;

        public Attribute(
                Type parentType,
                Set<Modifier.Keyword> modifiers,
                com.github.javaparser.ast.type.Type variableType,
                SimpleName name) {
            this.parentType = parentType;
            this.modifiers = modifiers;
            this.variableType = variableType;
            this.name = name;
        }

        public Type getParentType() {
            return parentType;
        }

        public boolean isProtected() {
            return modifiers.contains(Modifier.Keyword.PROTECTED);
        }

        public boolean isPrivate() {
            return modifiers.contains(Modifier.Keyword.PRIVATE);
        }

        public boolean isPublic() {
            return modifiers.contains(Modifier.Keyword.PUBLIC);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Attribute attribute = (Attribute) o;
            return Objects.equals(variableType.asString(), attribute.variableType.asString())
                    && Objects.equals(name.getIdentifier(), attribute.name.getIdentifier());
        }

        @Override
        public int hashCode() {
            return Objects.hash(variableType.asString(), name.getIdentifier());
        }

        public boolean isPublicish() {
            return !modifiers.contains(Modifier.Keyword.PRIVATE)
                    && !modifiers.contains(Modifier.Keyword.PROTECTED); // Public and default are both public enough
        }
    }
}
