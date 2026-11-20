/*  Copyright (C) 2025-2026 WAPEETY

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
package app.superhealth.engine.util.language.impl;

import java.util.HashMap;

import app.superhealth.engine.util.language.SimpleTransliterator;

public class ItalianTransliterator extends SimpleTransliterator {
    public ItalianTransliterator() {
        super(new HashMap<Character, String>() {{
            put('à',"a'");put('á',"a'");
            put('è',"e'");put('é',"e'");
            put('ì',"i'");put('í',"i'");
            put('ò',"o'");put('ó',"o'");
            put('ù',"u'");put('ú',"u'");
        }});
    }
}
