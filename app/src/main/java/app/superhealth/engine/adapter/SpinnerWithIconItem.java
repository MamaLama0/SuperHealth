/*  Copyright (C) 2020-2024 Petr Vaněk

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
package app.superhealth.engine.adapter;

public class SpinnerWithIconItem {

    String text;
    Long id;
    int imageId;

    public SpinnerWithIconItem(String text, Long id, int imageId) {
        this.text = text;
        this.id = id;
        this.imageId = imageId;
    }

    public String getText() {
        return text;
    }

    public int getImageId() {
        return imageId;
    }

    public Long getId() {
        return id;
    }

    @Override
    public String toString() {
        return text;
    }

}
