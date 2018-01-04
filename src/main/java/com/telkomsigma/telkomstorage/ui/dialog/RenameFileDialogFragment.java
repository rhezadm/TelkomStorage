/**
 *   ownCloud Android client application
 *
 *   @author David A. Velasco
 *   Copyright (C) 2014 ownCloud Inc.
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License version 2,
 *   as published by the Free Software Foundation.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.telkomsigma.telkomstorage.ui.dialog;

/**
 *  Dialog to input a new name for an {@link OCFile} being renamed.  
 * 
 *  Triggers the rename operation. 
 */

import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager.LayoutParams;
import android.widget.EditText;
import android.widget.TextView;

import com.telkomsigma.telkomstorage.datamodel.OCFile;
import com.owncloud.android.lib.resources.files.FileUtils;
import com.telkomsigma.telkomstorage.ui.activity.ComponentsGetter;
import com.telkomsigma.telkomstorage.utils.DisplayUtils;
import com.telkomsigma.telkomstorage.utils.ThemeUtils;


/**
 *  Dialog to input a new name for a file or folder to rename.  
 * 
 *  Triggers the rename operation when name is confirmed.
 */
public class RenameFileDialogFragment
        extends DialogFragment implements DialogInterface.OnClickListener {

    private static final String ARG_TARGET_FILE = "TARGET_FILE";

    private OCFile mTargetFile;

    /**
     * Public factory method to create new RenameFileDialogFragment instances.
     * 
     * @param file            File to rename.
     * @return                Dialog ready to show.
     */
    public static RenameFileDialogFragment newInstance(OCFile file) {
        RenameFileDialogFragment frag = new RenameFileDialogFragment();
        Bundle args = new Bundle();
        args.putParcelable(ARG_TARGET_FILE, file);
        frag.setArguments(args);
        return frag;
        
    }

    @Override
    public void onStart() {
        super.onStart();

        int color = ThemeUtils.primaryAccentColor();

        AlertDialog alertDialog = (AlertDialog) getDialog();

        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(color);
        alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(color);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        int accentColor = ThemeUtils.primaryAccentColor();
        mTargetFile = getArguments().getParcelable(ARG_TARGET_FILE);

        // Inflate the layout for the dialog
        LayoutInflater inflater = getActivity().getLayoutInflater();
        View v = inflater.inflate(com.telkomsigma.telkomstorage.R.layout.edit_box_dialog, null);
        
        // Setup layout 
        String currentName = mTargetFile.getFileName();
        EditText inputText = ((EditText)v.findViewById(com.telkomsigma.telkomstorage.R.id.user_input));
        inputText.setText(currentName);
        int selectionStart = 0;
        int extensionStart = mTargetFile.isFolder() ? -1 : currentName.lastIndexOf('.');
        int selectionEnd = (extensionStart >= 0) ? extensionStart : currentName.length();
        if (selectionStart >= 0 && selectionEnd >= 0) {
            inputText.setSelection(
                    Math.min(selectionStart, selectionEnd), 
                    Math.max(selectionStart, selectionEnd));
        }
        inputText.requestFocus();
        inputText.getBackground().setColorFilter(accentColor, PorterDuff.Mode.SRC_ATOP);
        
        // Build the dialog  
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setView(v)
               .setPositiveButton(com.telkomsigma.telkomstorage.R.string.common_ok, this)
               .setNegativeButton(com.telkomsigma.telkomstorage.R.string.common_cancel, this)
                .setTitle(ThemeUtils.getColoredTitle(getResources().getString(com.telkomsigma.telkomstorage.R.string.rename_dialog_title),
                        accentColor));
        Dialog d = builder.create();
        d.getWindow().setSoftInputMode(LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        return d;
    }    

    
    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == AlertDialog.BUTTON_POSITIVE) {
            String newFileName = 
                ((TextView)(getDialog().findViewById(com.telkomsigma.telkomstorage.R.id.user_input)))
                    .getText().toString().trim();
            
            if (newFileName.length() <= 0) {
                DisplayUtils.showSnackMessage(getActivity(), com.telkomsigma.telkomstorage.R.string.filename_empty);
                return;
            }

            boolean serverWithForbiddenChars = ((ComponentsGetter)getActivity()).
                    getFileOperationsHelper().isVersionWithForbiddenCharacters();

            if (!FileUtils.isValidName(newFileName, serverWithForbiddenChars)) {

                if (serverWithForbiddenChars) {
                    DisplayUtils.showSnackMessage(getActivity(), com.telkomsigma.telkomstorage.R.string.filename_forbidden_charaters_from_server);
                } else {
                    DisplayUtils.showSnackMessage(getActivity(), com.telkomsigma.telkomstorage.R.string.filename_forbidden_characters);
                }

                return;
            }

            ((ComponentsGetter)getActivity()).getFileOperationsHelper().
                    renameFile(mTargetFile, newFileName);

        }
    }
}
