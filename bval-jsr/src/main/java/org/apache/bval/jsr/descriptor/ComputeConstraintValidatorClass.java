package org.apache.bval.jsr.descriptor;

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.validation.ConstraintDefinitionException;
import javax.validation.ConstraintValidator;
import javax.validation.UnexpectedTypeException;
import javax.validation.constraintvalidation.ValidationTarget;

import org.apache.bval.jsr.ApacheValidatorFactory;
import org.apache.bval.jsr.ConstraintCached.ConstraintValidatorInfo;
import org.apache.bval.jsr.util.Exceptions;
import org.apache.bval.util.Validate;
import org.apache.bval.util.reflection.Reflection;
import org.apache.bval.util.reflection.Reflection.Interfaces;
import org.apache.bval.util.reflection.TypeUtils;
import org.apache.commons.weaver.privilizer.Privilizing;
import org.apache.commons.weaver.privilizer.Privilizing.CallTo;

@Privilizing(@CallTo(Reflection.class))
class ComputeConstraintValidatorClass<A extends Annotation>
    implements Supplier<Class<? extends ConstraintValidator<A, ?>>> {

    private static final String CV = ConstraintValidator.class.getSimpleName();
    private static final WildcardType UNBOUNDED = TypeUtils.wildcardType().build();

    private static Type getValidatedType(Class<? extends ConstraintValidator<?, ?>> validatorType) {
        final Type result = TypeUtils.getTypeArguments(validatorType, ConstraintValidator.class)
            .get(ConstraintValidator.class.getTypeParameters()[1]);
        Exceptions.raiseUnless(isSupported(result), ConstraintDefinitionException::new,
            "Validated type %s declared by %s %s is unsupported", result, CV, validatorType.getName());
        return result;
    }

    private static boolean isSupported(Type validatedType) {
        if (validatedType instanceof Class<?>) {
            return true;
        }
        if (validatedType instanceof ParameterizedType) {
            return Stream.of(((ParameterizedType) validatedType).getActualTypeArguments())
                .allMatch(arg -> TypeUtils.equals(arg, UNBOUNDED));
        }
        return false;
    }

    private final ApacheValidatorFactory validatorFactory;
    private final Class<A> constraintType;
    private final Class<?> validatedType;
    private final ValidationTarget validationTarget;

    ComputeConstraintValidatorClass(ApacheValidatorFactory validatorFactory, ValidationTarget validationTarget,
        Class<A> constraintType, Class<?> validatedType) {
        super();
        this.validatorFactory = Validate.notNull(validatorFactory, "validatorFactory");
        this.validationTarget = Validate.notNull(validationTarget, "validationTarget");
        this.constraintType = Validate.notNull(constraintType, "constraintType");
        this.validatedType = Validate.notNull(validatedType, "validatedType");
    }

    @Override
    public Class<? extends ConstraintValidator<A, ?>> get() {
        final Optional<Class<? extends ConstraintValidator<A, ?>>> result =
            validatorFactory.getConstraintsCache().getConstraintValidatorInfo(constraintType).map(this::findValidator);

        Exceptions.raiseUnless(result.isPresent(), UnexpectedTypeException::new,
            "No %s found for %s constraint %s/type %s", CV, validationTarget, constraintType, validatedType);

        return result.get();
    }

    private Class<? extends ConstraintValidator<A, ?>> findValidator(Set<ConstraintValidatorInfo<A>> infos) {
        switch (validationTarget) {
            case PARAMETERS:
                return findCrossParameterValidator(infos);
            case ANNOTATED_ELEMENT:
                return findAnnotatedElementValidator(infos);
            default:
                return null;
        }
    }

    private Class<? extends ConstraintValidator<A, ?>> findCrossParameterValidator(
        Set<ConstraintValidatorInfo<A>> infos) {

        final Set<ConstraintValidatorInfo<A>> set =
            infos.stream().filter(info -> info.getSupportedTargets().contains(ValidationTarget.PARAMETERS))
                .collect(Collectors.toSet());

        Exceptions.raiseIf(set.isEmpty(), UnexpectedTypeException::new,
            "No cross-parameter %s found for constraint type %s", CV, constraintType);

        Exceptions.raiseUnless(set.size() == 1, UnexpectedTypeException::new,
            "%d cross-parameter %ss found for constraint type %s", set.size(), CV, constraintType);

        final Class<? extends ConstraintValidator<A, ?>> result = set.iterator().next().getType();
        Exceptions.raiseUnless(TypeUtils.isAssignable(Object[].class, getValidatedType(result)),
            ConstraintDefinitionException::new,
            "Cross-parameter %s %s does not support the validation of an object array", CV, result.getName());

        return result;
    }

    private Class<? extends ConstraintValidator<A, ?>> findAnnotatedElementValidator(
        Set<ConstraintValidatorInfo<A>> infos) {

        final Class<?> effectiveValidatedType = Reflection.primitiveToWrapper(validatedType);

        final Map<Type, Class<? extends ConstraintValidator<?, ?>>> validators =
            infos.stream().filter(info -> info.getSupportedTargets().contains(ValidationTarget.ANNOTATED_ELEMENT))
                .map(ConstraintValidatorInfo::getType)
                .collect(Collectors.toMap(ComputeConstraintValidatorClass::getValidatedType, Function.identity()));

        final Map<Type, Class<? extends ConstraintValidator<?, ?>>> candidates = new HashMap<>();

        for (Class<?> type : Reflection.hierarchy(effectiveValidatedType, Interfaces.INCLUDE)) {
            if (validators.containsKey(type)) {
                // if we already have a candidate whose validated type is a subtype of the current evaluated type, skip:
                if (candidates.keySet().stream().anyMatch(k -> TypeUtils.isAssignable(k, type))) {
                    continue;
                }
                candidates.put(type, validators.get(type));
            }
        }

        Exceptions.raiseIf(candidates.isEmpty(), UnexpectedTypeException::new,
            "No compliant %s found for annotated element of type %s", CV, TypeUtils.toString(validatedType));

        Exceptions.raiseIf(candidates.size() > 1, UnexpectedTypeException::new,
            "> 1 maximally specific %s found for annotated element of type %s", CV, TypeUtils.toString(validatedType));

        @SuppressWarnings("unchecked")
        final Class<? extends ConstraintValidator<A, ?>> result =
            (Class<? extends ConstraintValidator<A, ?>>) candidates.values().iterator().next();
        return result;
    }
}
