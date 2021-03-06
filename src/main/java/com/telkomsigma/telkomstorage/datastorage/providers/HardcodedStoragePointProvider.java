/**
 *   Nextcloud Android client application
 *
 *   @author Bartosz Przybylski
 *   Copyright (C) 2016 Nextcloud
 *   Copyright (C) 2016 Bartosz Przybylski
 *
 *   This program is free software; you can redistribute it and/or
 *   modify it under the terms of the GNU AFFERO GENERAL PUBLIC LICENSE
 *   License as published by the Free Software Foundation; either
 *   version 3 of the License, or any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU AFFERO GENERAL PUBLIC LICENSE for more details.
 *
 *   You should have received a copy of the GNU Affero General Public
 *   License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.telkomsigma.telkomstorage.datastorage.providers;

import com.telkomsigma.telkomstorage.datastorage.StoragePoint;

import java.util.Vector;

/**
 * @author Bartosz Przybylski
 */
public class HardcodedStoragePointProvider extends AbstractStoragePointProvider {

    private static final String[] PATHS = {
            "/mnt/external_sd/",
            "/mnt/extSdCard/",
            "/storage/extSdCard",
            "/storage/sdcard1/",
            "/storage/usbcard1/"
    };

    @Override
    public boolean canProvideStoragePoints() {
        return true;
    }

    @Override
    public Vector<StoragePoint> getAvailableStoragePoint() {
        Vector<StoragePoint> result = new Vector<>();

        for (String s : PATHS) {
            if (canBeAddedToAvailableList(result, s)) {
                result.add(new StoragePoint(s, s));
            }
        }

        return result;
    }
}
