package cgeo.geocaching.filters.core;

import cgeo.geocaching.R;
import cgeo.geocaching.storage.SqlBuilder;
import cgeo.geocaching.utils.LocalizationUtils;

import androidx.annotation.StringRes;

import java.util.regex.Pattern;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.StringUtils;


public class StringFilter {

    public enum StringFilterType {
        IS_PRESENT(R.string.cache_filter_stringfilter_type_is_present),
        IS_NOT_PRESENT(R.string.cache_filter_stringfilter_type_is_not_present),
        CONTAINS(R.string.cache_filter_stringfilter_type_contains),
        STARTS_WITH(R.string.cache_filter_stringfilter_type_starts_with),
        ENDS_WITH(R.string.cache_filter_stringfilter_type_ends_with),
        PATTERN(R.string.cache_filter_stringfilter_type_pattern);

        private final int resId;

        StringFilterType(@StringRes final int resId) {
            this.resId = resId;
        }

        public String toUserDisplayableString() {
            return LocalizationUtils.getStringWithFallback(resId, name());
        }
    }

    private StringFilterType filterType = getDefaultFilterType();
    private String textValue;
    private boolean matchCase;

    public static StringFilterType getDefaultFilterType() {
        return StringFilterType.CONTAINS;
    }

    public boolean isFilled() {
        return !StringUtils.isBlank(textValue) || filterType == StringFilterType.IS_NOT_PRESENT || filterType == StringFilterType.IS_PRESENT;
    }

    public StringFilterType getFilterType() {
        return filterType;
    }

    public void setFilterType(final StringFilterType filterType) {
        this.filterType = filterType == null ? getDefaultFilterType() : filterType;
    }

    public String getTextValue() {
        return textValue;
    }

    public void setTextValue(final String textValue) {
        this.textValue = textValue;
    }

    public boolean isMatchCase() {
        return matchCase;
    }

    public void setMatchCase(final boolean matchCase) {
        this.matchCase = matchCase;
    }

    public void setFromConfig(final String[] config, final int startPos) {
        if (config != null && config.length > startPos) {
            setTextValue(config[startPos]);
            setFilterType(config.length > 1 ? EnumUtils.getEnum(StringFilterType.class,  config[startPos + 1], StringFilterType.CONTAINS) : StringFilterType.CONTAINS);
            setMatchCase(config.length > 2 && BooleanUtils.toBoolean(config[startPos + 2]));
        }
    }

    public String[] addToConfig(final String[] config, final int startPos) {
        if (config == null || config.length < startPos + 3) {
            return config;
        }
        config[startPos] = textValue;
        config[startPos + 1] = filterType.name();
        config[startPos + 2] = String.valueOf(matchCase);
        return config;
    }

    public boolean matches(final String value) {
        if (!isFilled()) {
            return true;
        }
        final boolean valueIsEmpty = StringUtils.isEmpty(value);
        if (filterType == StringFilterType.IS_NOT_PRESENT) {
            return valueIsEmpty;
        }
        if (filterType == StringFilterType.IS_PRESENT) {
            return !valueIsEmpty;
        }

        final String matchGcValue = this.matchCase ? value : StringUtils.lowerCase(value);
        final String matchTextValue = this.matchCase ? this.textValue : StringUtils.lowerCase(this.textValue);
        switch (this.filterType) {
            case CONTAINS:
                return matchGcValue.contains(matchTextValue);
            case STARTS_WITH:
                return matchGcValue.startsWith(matchTextValue);
            case ENDS_WITH:
                return matchGcValue.endsWith(matchTextValue);
            case PATTERN:
                return Pattern.compile(matchTextValue.replace('?', '.').replaceAll("\\*", ".*")).matcher(matchGcValue).matches();
            default:
                //can never happen
                return true;
        }
    }

    public void addToSql(final SqlBuilder sqlBuilder, final String columnExpression) {
        if (!isFilled()) {
            sqlBuilder.addWhereAlwaysInclude();
        } else {
            switch (filterType) {
                case IS_NOT_PRESENT:
                    sqlBuilder.addWhere(columnExpression + " IS NULL OR " + columnExpression + " = ''");
                    break;
                case IS_PRESENT:
                    sqlBuilder.addWhere(columnExpression + " IS NOT NULL AND " + columnExpression + " <> ''");
                    break;
                default:
                    sqlBuilder.addWhere(getRawLikeSqlExpression(columnExpression));
                    break;
            }
        }
    }

    public void addToSqlForSubquery(final SqlBuilder sqlBuilder, final String subQuery, final boolean subQueryContainsWhere, final String subQueryColumn) {
        if (!isFilled()) {
            sqlBuilder.addWhereAlwaysInclude();
        } else {
            switch (filterType) {
                case IS_NOT_PRESENT:
                    sqlBuilder.addWhere("NOT EXISTS( " + subQuery + ")");
                    break;
                case IS_PRESENT:
                    sqlBuilder.addWhere("EXISTS(" + subQuery + ")");
                    break;
                default:
                    final StringBuilder sb = new StringBuilder("EXISTS(" + subQuery + (subQueryContainsWhere ? " AND " : " WHERE "));
                    sb.append(getRawLikeSqlExpression(subQueryColumn)).append(")");
                    sqlBuilder.addWhere(sb.toString());
                    break;
            }
        }
    }

    private String getRawLikeSqlExpression(final String columnExpression) {
        final StringBuilder sb = new StringBuilder();

        if (!StringUtils.isBlank(this.textValue)) {
            String matchTextValue = SqlBuilder.escape(this.matchCase ? this.textValue : StringUtils.lowerCase(this.textValue), true);
            if (!this.matchCase) {
                sb.append("LOWER(");
            }
            sb.append(columnExpression);
            if (!this.matchCase) {
                sb.append(")");
            }

            switch (this.filterType) {
                case CONTAINS:
                    matchTextValue = "%" + matchTextValue + "%";
                    break;
                case STARTS_WITH:
                    matchTextValue = matchTextValue + "%";
                    break;
                case ENDS_WITH:
                    matchTextValue = "%" + matchTextValue;
                    break;
                case PATTERN:
                default:
                    matchTextValue = matchTextValue.replace('*', '%').replace('?', '_');
                    break;
            }
            sb.append(SqlBuilder.createLikeExpression(matchTextValue));
        }
        return sb.toString();
    }

}
