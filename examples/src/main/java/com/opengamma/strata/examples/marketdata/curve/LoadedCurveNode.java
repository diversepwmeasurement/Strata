/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.strata.examples.marketdata.curve;

import java.time.LocalDate;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.joda.beans.Bean;
import org.joda.beans.BeanBuilder;
import org.joda.beans.BeanDefinition;
import org.joda.beans.ImmutableBean;
import org.joda.beans.JodaBeanUtils;
import org.joda.beans.MetaProperty;
import org.joda.beans.Property;
import org.joda.beans.PropertyDefinition;
import org.joda.beans.impl.direct.DirectFieldsBeanBuilder;
import org.joda.beans.impl.direct.DirectMetaBean;
import org.joda.beans.impl.direct.DirectMetaProperty;
import org.joda.beans.impl.direct.DirectMetaPropertyMap;

import com.google.common.collect.ComparisonChain;

/**
 * Represents a node on a calibrated curve.
 */
@BeanDefinition(builderScope = "private")
public final class LoadedCurveNode
    implements Comparable<LoadedCurveNode>, ImmutableBean {

  /**
   * The date that the curve node value applies to.
   */
  @PropertyDefinition(validate = "notNull")
  private final LocalDate date;
  /**
   * The value of the curve at the node.
   */
  @PropertyDefinition
  private final double value;
  /**
   * The label to use for the node.
   */
  @PropertyDefinition(validate = "notNull")
  private final String label;

  /**
   * Creates an instance.
   * 
   * @param date  the date of the node
   * @param value  the value of the node
   * @param label  the label of the node
   * @return the curve node
   */
  public static LoadedCurveNode of(LocalDate date, double value, String label) {
    return new LoadedCurveNode(date, value, label);
  }

  //-------------------------------------------------------------------------
  /**
   * Compares this node to another by date.
   * 
   * @param other  the other curve node
   * @return negative if this node is earlier, positive if later and zero if equal
   */
  @Override
  public int compareTo(LoadedCurveNode other) {
    return ComparisonChain.start()
        .compare(getDate(), other.getDate())
        .compare(getValue(), other.getValue())
        .compare(getLabel(), other.getLabel())
        .result();
  }

  //------------------------- AUTOGENERATED START -------------------------
  ///CLOVER:OFF
  /**
   * The meta-bean for {@code LoadedCurveNode}.
   * @return the meta-bean, not null
   */
  public static LoadedCurveNode.Meta meta() {
    return LoadedCurveNode.Meta.INSTANCE;
  }

  static {
    JodaBeanUtils.registerMetaBean(LoadedCurveNode.Meta.INSTANCE);
  }

  private LoadedCurveNode(
      LocalDate date,
      double value,
      String label) {
    JodaBeanUtils.notNull(date, "date");
    JodaBeanUtils.notNull(label, "label");
    this.date = date;
    this.value = value;
    this.label = label;
  }

  @Override
  public LoadedCurveNode.Meta metaBean() {
    return LoadedCurveNode.Meta.INSTANCE;
  }

  @Override
  public <R> Property<R> property(String propertyName) {
    return metaBean().<R>metaProperty(propertyName).createProperty(this);
  }

  @Override
  public Set<String> propertyNames() {
    return metaBean().metaPropertyMap().keySet();
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the date that the curve node value applies to.
   * @return the value of the property, not null
   */
  public LocalDate getDate() {
    return date;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the value of the curve at the node.
   * @return the value of the property
   */
  public double getValue() {
    return value;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the label to use for the node.
   * @return the value of the property, not null
   */
  public String getLabel() {
    return label;
  }

  //-----------------------------------------------------------------------
  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj != null && obj.getClass() == this.getClass()) {
      LoadedCurveNode other = (LoadedCurveNode) obj;
      return JodaBeanUtils.equal(getDate(), other.getDate()) &&
          JodaBeanUtils.equal(getValue(), other.getValue()) &&
          JodaBeanUtils.equal(getLabel(), other.getLabel());
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = getClass().hashCode();
    hash = hash * 31 + JodaBeanUtils.hashCode(getDate());
    hash = hash * 31 + JodaBeanUtils.hashCode(getValue());
    hash = hash * 31 + JodaBeanUtils.hashCode(getLabel());
    return hash;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder(128);
    buf.append("LoadedCurveNode{");
    buf.append("date").append('=').append(getDate()).append(',').append(' ');
    buf.append("value").append('=').append(getValue()).append(',').append(' ');
    buf.append("label").append('=').append(JodaBeanUtils.toString(getLabel()));
    buf.append('}');
    return buf.toString();
  }

  //-----------------------------------------------------------------------
  /**
   * The meta-bean for {@code LoadedCurveNode}.
   */
  public static final class Meta extends DirectMetaBean {
    /**
     * The singleton instance of the meta-bean.
     */
    static final Meta INSTANCE = new Meta();

    /**
     * The meta-property for the {@code date} property.
     */
    private final MetaProperty<LocalDate> date = DirectMetaProperty.ofImmutable(
        this, "date", LoadedCurveNode.class, LocalDate.class);
    /**
     * The meta-property for the {@code value} property.
     */
    private final MetaProperty<Double> value = DirectMetaProperty.ofImmutable(
        this, "value", LoadedCurveNode.class, Double.TYPE);
    /**
     * The meta-property for the {@code label} property.
     */
    private final MetaProperty<String> label = DirectMetaProperty.ofImmutable(
        this, "label", LoadedCurveNode.class, String.class);
    /**
     * The meta-properties.
     */
    private final Map<String, MetaProperty<?>> metaPropertyMap$ = new DirectMetaPropertyMap(
        this, null,
        "date",
        "value",
        "label");

    /**
     * Restricted constructor.
     */
    private Meta() {
    }

    @Override
    protected MetaProperty<?> metaPropertyGet(String propertyName) {
      switch (propertyName.hashCode()) {
        case 3076014:  // date
          return date;
        case 111972721:  // value
          return value;
        case 102727412:  // label
          return label;
      }
      return super.metaPropertyGet(propertyName);
    }

    @Override
    public BeanBuilder<? extends LoadedCurveNode> builder() {
      return new LoadedCurveNode.Builder();
    }

    @Override
    public Class<? extends LoadedCurveNode> beanType() {
      return LoadedCurveNode.class;
    }

    @Override
    public Map<String, MetaProperty<?>> metaPropertyMap() {
      return metaPropertyMap$;
    }

    //-----------------------------------------------------------------------
    /**
     * The meta-property for the {@code date} property.
     * @return the meta-property, not null
     */
    public MetaProperty<LocalDate> date() {
      return date;
    }

    /**
     * The meta-property for the {@code value} property.
     * @return the meta-property, not null
     */
    public MetaProperty<Double> value() {
      return value;
    }

    /**
     * The meta-property for the {@code label} property.
     * @return the meta-property, not null
     */
    public MetaProperty<String> label() {
      return label;
    }

    //-----------------------------------------------------------------------
    @Override
    protected Object propertyGet(Bean bean, String propertyName, boolean quiet) {
      switch (propertyName.hashCode()) {
        case 3076014:  // date
          return ((LoadedCurveNode) bean).getDate();
        case 111972721:  // value
          return ((LoadedCurveNode) bean).getValue();
        case 102727412:  // label
          return ((LoadedCurveNode) bean).getLabel();
      }
      return super.propertyGet(bean, propertyName, quiet);
    }

    @Override
    protected void propertySet(Bean bean, String propertyName, Object newValue, boolean quiet) {
      metaProperty(propertyName);
      if (quiet) {
        return;
      }
      throw new UnsupportedOperationException("Property cannot be written: " + propertyName);
    }

  }

  //-----------------------------------------------------------------------
  /**
   * The bean-builder for {@code LoadedCurveNode}.
   */
  private static final class Builder extends DirectFieldsBeanBuilder<LoadedCurveNode> {

    private LocalDate date;
    private double value;
    private String label;

    /**
     * Restricted constructor.
     */
    private Builder() {
    }

    //-----------------------------------------------------------------------
    @Override
    public Object get(String propertyName) {
      switch (propertyName.hashCode()) {
        case 3076014:  // date
          return date;
        case 111972721:  // value
          return value;
        case 102727412:  // label
          return label;
        default:
          throw new NoSuchElementException("Unknown property: " + propertyName);
      }
    }

    @Override
    public Builder set(String propertyName, Object newValue) {
      switch (propertyName.hashCode()) {
        case 3076014:  // date
          this.date = (LocalDate) newValue;
          break;
        case 111972721:  // value
          this.value = (Double) newValue;
          break;
        case 102727412:  // label
          this.label = (String) newValue;
          break;
        default:
          throw new NoSuchElementException("Unknown property: " + propertyName);
      }
      return this;
    }

    @Override
    public Builder set(MetaProperty<?> property, Object value) {
      super.set(property, value);
      return this;
    }

    @Override
    public Builder setString(String propertyName, String value) {
      setString(meta().metaProperty(propertyName), value);
      return this;
    }

    @Override
    public Builder setString(MetaProperty<?> property, String value) {
      super.setString(property, value);
      return this;
    }

    @Override
    public Builder setAll(Map<String, ? extends Object> propertyValueMap) {
      super.setAll(propertyValueMap);
      return this;
    }

    @Override
    public LoadedCurveNode build() {
      return new LoadedCurveNode(
          date,
          value,
          label);
    }

    //-----------------------------------------------------------------------
    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder(128);
      buf.append("LoadedCurveNode.Builder{");
      buf.append("date").append('=').append(JodaBeanUtils.toString(date)).append(',').append(' ');
      buf.append("value").append('=').append(JodaBeanUtils.toString(value)).append(',').append(' ');
      buf.append("label").append('=').append(JodaBeanUtils.toString(label));
      buf.append('}');
      return buf.toString();
    }

  }

  ///CLOVER:ON
  //-------------------------- AUTOGENERATED END --------------------------
}
