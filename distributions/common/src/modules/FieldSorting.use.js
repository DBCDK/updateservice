use("Marc");
use("MarcClasses");
use("Log");
use("StringUtil");

EXPORTED_SYMBOLS = ['FieldSorting'];

var FieldSorting = function () {
    var sortingList;
    var BUNDLE_NAME = "validation";

    /**
     * The sort function is responsible for sorting the subfield on the given field
     * The sort order is defined by the sorting attribute on the input field
     * Note that the amount of subfields in sorting and the actual amount of subfields on the field might not match
     * Any subfields not listed in the sorting list will be moved to after the subfields listed in the sorting list
     *
     * @param field - the field must include both sorting list and subfields
     *
     * Returns the field object with the subfields ordered after the sorting list
     */
    function sort(field) {
        Log.trace("Enter - FieldSorting.sort", field);
        try {

            var bundle = ResourceBundleFactory.getBundle(BUNDLE_NAME);

            if (field.sorting === undefined || field.sorting === null) {
                Log.debug(ResourceBundle.getString(bundle, "fieldSorting.sort.sorting.error"));
                return field;
            }

            if (field.subfields === undefined || field.subfields === null) {
                Log.debug(ResourceBundle.getString(bundle, "fieldSorting.sort.subfields.error"));
                return field;
            }

            sortingList = field.sorting;

            var keys = Object.keys(field.subfields);

            keys.sort(function (a, b) {
                var indexA = findIndexInSortingList(a);
                var indexB = findIndexInSortingList(b);

                return indexA - indexB;
            });

            var sortedSubfields = {};

            for (var i = 0; i < keys.length; i++) {
                sortedSubfields[keys[i]] = field.subfields[keys[i]];
            }

            field.subfields = sortedSubfields;

            return field;
        } finally {
            Log.trace("Exit -- FieldSorting.sort");
        }
    }


    /**
     * The sorting rules are as follow:
     * * Subfields that match the sorting list should appear in the defined sort order
     * * If a subfield name is upper case but only the lower case version is in the sort order then that field
     * should appear right before the lower case subfield
     * * A subfield name not defined in the sort order should just be moved to the end of the list
     *
     * Note 1: Lower value = earlier in the list
     * Note 2: Only lower case subfield names are listed in the sorting list
     *
     * @param arg subfield name
     * @returns the index of the subfield name
     */
    function findIndexInSortingList(arg) {
        var index;

        if (sortingList.indexOf(arg) > -1) {
            index = sortingList.indexOf(arg);
        } else if (sortingList.indexOf(arg.toLowerCase()) > -1) {
            // Upper case should be before lower case so we give it a slight nudge forward
            index = sortingList.indexOf(arg.toLowerCase()) - 0.5;
        } else {
            index = 999; // Not found so move to the end of the list
        }

        return index;
    }

    return {
        'BUNDLE_NAME': BUNDLE_NAME,
        'sort': sort
    }
}();
