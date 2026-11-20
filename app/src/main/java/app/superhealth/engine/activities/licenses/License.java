/*  Copyright (C) 2025 José Rebelo

    This file is part of SuperHealth.

    SuperHealth is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    SuperHealth is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package app.superhealth.engine.activities.licenses;

import app.superhealth.engine.util.gson.GsonSerialized;

@GsonSerialized
public class License {
    private final String name;
    private final String owner;
    private final String type;
    private final String url;
    private final String path;

    public License(final String name, final String owner, final String type, final String url, final String path) {
        this.name = name;
        this.owner = owner;
        this.type = type;
        this.url = url;
        this.path = path;
    }

    public String getName() {
        return name;
    }

    public String getOwner() {
        return owner;
    }

    public String getType() {
        return type;
    }

    public String getUrl() {
        return url;
    }

    public String getPath() {
        return path;
    }
}
