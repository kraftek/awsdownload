/*
 * Copyright (C) 2016 Cosmin Cara
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 *  with this program; if not, see http://www.gnu.org/licenses/
 */
package ro.cs.products.sentinel2.workaround;

/**
 * Types of operation supported for treating the angles grids.
 *
 * @author Cosmin Cara
 */
public enum FillAnglesMethod {
    /*
     * Nothing to do
     */
    NONE,
    /*
     * Missing angle value replaced with NaN
     */
    NAN,
    /*
     * Missing angle value interpolated from existing values
     */
    INTERPOLATE
}
