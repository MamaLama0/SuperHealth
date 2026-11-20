/*  Copyright (C) 2022-2024 José Rebelo

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

public class ScandinavianTransliterator extends SimpleTransliterator {
    public ScandinavianTransliterator() {
        super(new HashMap<Character, String>() {{
            put('Æ',"Ae"); put('æ',"ae");
            put('Ø',"Oe"); put('ø',"oe");
            put('Å',"Aa"); put('å',"aa");
        }});
    }
}
