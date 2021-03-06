package cgeo.geocaching.filters.gui;

import cgeo.geocaching.R;
import cgeo.geocaching.filters.core.IGeocacheFilter;
import cgeo.geocaching.models.Geocache;

import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

public class CheckboxFilterViewHolder<T, F extends IGeocacheFilter> extends BaseFilterViewHolder<F> {

    private final ValueGroupFilterAccessor<T, F> filterAccessor;
    private final CheckBox[] valueCheckboxes;

    private CheckBox selectAllNoneCheckbox;
    private boolean selectAllNoneBroadcast = true;

    public CheckboxFilterViewHolder(final ValueGroupFilterAccessor<T, F> filterAccessor) {
        this.filterAccessor = filterAccessor;
        this.valueCheckboxes = new CheckBox[filterAccessor.getSelectableValuesAsArray().length];
    }


    public View createView() {

        final Map<T, Integer> stats = calculateStatistics();
        final boolean showStatistics = stats != null;
        final boolean statsAreComplete = !showStatistics || FilterViewHolderCreator.isListInfoComplete();


        final LinearLayout ll = new LinearLayout(getActivity());
        ll.setOrientation(LinearLayout.VERTICAL);

        //selectall/none
        if (filterAccessor.getSelectableValuesAsArray().length > 1) {
            selectAllNoneCheckbox = FilterGuiUtils.addCheckboxProperty(getActivity(), ll, R.string.cache_filter_checkboxlist_selectallnone, R.drawable.ic_menu_selectall, 0);
            selectAllNoneCheckbox.setOnCheckedChangeListener((v, c) -> {
                if (!selectAllNoneBroadcast) {
                    return;
                }
                for (final CheckBox cb : this.valueCheckboxes) {
                    cb.setChecked(c);
                }
            });
        }

        int idx = 0;
        for (T value : filterAccessor.getSelectableValuesAsArray()) {

            final String vText = this.filterAccessor.getDisplayText(value) + (showStatistics ? " (" + (stats.containsKey(value) ? "" + stats.get(value) : "0") + (statsAreComplete ? "" : "+") + ")" : "");
            this.valueCheckboxes[idx] = FilterGuiUtils.addCheckboxProperty(getActivity(), ll, vText, this.filterAccessor.getIconFor(value), 0);
            this.valueCheckboxes[idx].setChecked(true);
            if (selectAllNoneCheckbox != null) {
                this.valueCheckboxes[idx].setOnCheckedChangeListener((v, c) -> {
                    checkAndSetAllNoneValue();
                });
            }
            idx++;
        }
        checkAndSetAllNoneValue();
        return ll;
    }

    @Nullable
    private Map<T, Integer> calculateStatistics() {
        Map<T, Integer> stats = null;
        if (FilterViewHolderCreator.isListInfoFilled() && filterAccessor.hasCacheValueGetter()) {
            stats = new HashMap<>();
            final F filter = createFilter();
            for (Geocache cache : FilterViewHolderCreator.getListInfoFilteredList()) {
                final Set<T> cValues = filterAccessor.getCacheValues(filter, cache);
                for (T cValue : cValues) {
                    if (stats.containsKey(cValue)) {
                        stats.put(cValue, stats.get(cValue) + 1);
                    } else {
                        stats.put(cValue, 1);
                    }
                }
            }
        }
        return stats;
    }

    private void checkAndSetAllNoneValue() {
        if (selectAllNoneCheckbox == null) {
            return;
        }

        boolean allChecked = true;
        for (final CheckBox cb : this.valueCheckboxes) {
            if (!cb.isChecked()) {
                allChecked = false;
                break;
            }
        }
        //avoid that setting all/none-checkbox leads to setting other checkbox values here
        selectAllNoneBroadcast = false;
        selectAllNoneCheckbox.setChecked(allChecked);
        selectAllNoneBroadcast = true;
    }

    @Override
    public void setViewFromFilter(final F filter) {
        final Collection<T> set = filterAccessor.getValues(filter);
        for (int i = 0; i < filterAccessor.getSelectableValuesAsArray().length; i++) {
            this.valueCheckboxes[i].setChecked(set.contains(filterAccessor.getSelectableValuesAsArray()[i]));
        }
    }

    @Override
    public F createFilterFromView() {
        final F filter = createFilter();
        final Set<T> set = new HashSet<>();
        for (int i = 0; i < filterAccessor.getSelectableValuesAsArray().length; i++) {
            if (this.valueCheckboxes[i].isChecked()) {
                set.add(filterAccessor.getSelectableValuesAsArray()[i]);
            }
        }
        filterAccessor.setValues(filter, set);
        return filter;
    }
}
