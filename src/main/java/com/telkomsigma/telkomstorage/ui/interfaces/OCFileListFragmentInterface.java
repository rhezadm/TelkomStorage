/**
 * Nextcloud Android client application
 *
 * @author Mario Danic
 * Copyright (C) 2017 Mario Danic
 * Copyright (C) 2017 Nextcloud GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.telkomsigma.telkomstorage.ui.interfaces;

import android.view.View;

import com.telkomsigma.telkomstorage.datamodel.OCFile;
import com.telkomsigma.telkomstorage.ui.adapter.FileListListAdapter;
import com.telkomsigma.telkomstorage.ui.fragment.OCFileListFragment;

/**
 * Interface for communication between {@link OCFileListFragment}
 * and {@link FileListListAdapter}
 */

public interface OCFileListFragmentInterface {
    void finishedFiltering();

    int getColumnSize();

    void onShareIconClick(OCFile file);

    void onOverflowIconClick(View view, OCFile file);
}
